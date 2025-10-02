#!/bin/bash

# CloudFront Setup Script for Musify CDN
# This script helps set up AWS CloudFront distribution for streaming

set -e

echo "🎵 Musify CloudFront Setup Script"
echo "================================"

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "❌ AWS CLI is not installed. Please install it first."
    echo "Visit: https://aws.amazon.com/cli/"
    exit 1
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    echo "❌ AWS credentials not configured. Run 'aws configure' first."
    exit 1
fi

# Variables
S3_BUCKET_NAME=${S3_BUCKET_NAME:-"musify-audio-content"}
CLOUDFRONT_COMMENT="Musify Audio Streaming CDN"
ORIGIN_ID="S3-${S3_BUCKET_NAME}"

# Create S3 bucket if it doesn't exist
echo "📦 Creating S3 bucket: ${S3_BUCKET_NAME}"
if aws s3api head-bucket --bucket "${S3_BUCKET_NAME}" 2>/dev/null; then
    echo "✅ Bucket already exists"
else
    aws s3 mb "s3://${S3_BUCKET_NAME}"
    echo "✅ Bucket created"
fi

# Configure S3 bucket for CloudFront
echo "🔧 Configuring S3 bucket..."

# Create bucket policy for CloudFront access
cat > /tmp/bucket-policy.json <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AllowCloudFrontAccess",
            "Effect": "Allow",
            "Principal": {
                "Service": "cloudfront.amazonaws.com"
            },
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::${S3_BUCKET_NAME}/*"
        }
    ]
}
EOF

aws s3api put-bucket-policy --bucket "${S3_BUCKET_NAME}" --policy file:///tmp/bucket-policy.json

# Enable CORS for the bucket
cat > /tmp/cors.json <<EOF
{
    "CORSRules": [
        {
            "AllowedHeaders": ["*"],
            "AllowedMethods": ["GET", "HEAD"],
            "AllowedOrigins": ["*"],
            "ExposeHeaders": ["ETag"],
            "MaxAgeSeconds": 3600
        }
    ]
}
EOF

aws s3api put-bucket-cors --bucket "${S3_BUCKET_NAME}" --cors-configuration file:///tmp/cors.json

# Create CloudFront distribution configuration
echo "☁️  Creating CloudFront distribution configuration..."

CALLER_REFERENCE=$(date +%s)

cat > /tmp/cloudfront-config.json <<EOF
{
    "CallerReference": "${CALLER_REFERENCE}",
    "Comment": "${CLOUDFRONT_COMMENT}",
    "DefaultRootObject": "",
    "Origins": {
        "Quantity": 1,
        "Items": [
            {
                "Id": "${ORIGIN_ID}",
                "DomainName": "${S3_BUCKET_NAME}.s3.amazonaws.com",
                "S3OriginConfig": {
                    "OriginAccessIdentity": ""
                }
            }
        ]
    },
    "DefaultCacheBehavior": {
        "TargetOriginId": "${ORIGIN_ID}",
        "ViewerProtocolPolicy": "redirect-to-https",
        "AllowedMethods": {
            "Quantity": 2,
            "Items": ["GET", "HEAD"],
            "CachedMethods": {
                "Quantity": 2,
                "Items": ["GET", "HEAD"]
            }
        },
        "ForwardedValues": {
            "QueryString": true,
            "Cookies": {
                "Forward": "none"
            },
            "Headers": {
                "Quantity": 3,
                "Items": ["Origin", "Access-Control-Request-Headers", "Access-Control-Request-Method"]
            }
        },
        "TrustedSigners": {
            "Enabled": false,
            "Quantity": 0
        },
        "MinTTL": 0,
        "DefaultTTL": 86400,
        "MaxTTL": 31536000,
        "Compress": true
    },
    "CacheBehaviors": {
        "Quantity": 2,
        "Items": [
            {
                "PathPattern": "*.m3u8",
                "TargetOriginId": "${ORIGIN_ID}",
                "ViewerProtocolPolicy": "redirect-to-https",
                "AllowedMethods": {
                    "Quantity": 2,
                    "Items": ["GET", "HEAD"],
                    "CachedMethods": {
                        "Quantity": 2,
                        "Items": ["GET", "HEAD"]
                    }
                },
                "ForwardedValues": {
                    "QueryString": false,
                    "Cookies": {
                        "Forward": "none"
                    }
                },
                "TrustedSigners": {
                    "Enabled": false,
                    "Quantity": 0
                },
                "MinTTL": 0,
                "DefaultTTL": 60,
                "MaxTTL": 60,
                "Compress": false
            },
            {
                "PathPattern": "*.ts",
                "TargetOriginId": "${ORIGIN_ID}",
                "ViewerProtocolPolicy": "redirect-to-https",
                "AllowedMethods": {
                    "Quantity": 2,
                    "Items": ["GET", "HEAD"],
                    "CachedMethods": {
                        "Quantity": 2,
                        "Items": ["GET", "HEAD"]
                    }
                },
                "ForwardedValues": {
                    "QueryString": false,
                    "Cookies": {
                        "Forward": "none"
                    }
                },
                "TrustedSigners": {
                    "Enabled": false,
                    "Quantity": 0
                },
                "MinTTL": 0,
                "DefaultTTL": 3600,
                "MaxTTL": 86400,
                "Compress": true
            }
        ]
    },
    "Enabled": true,
    "PriceClass": "PriceClass_100",
    "HttpVersion": "http2",
    "IsIPV6Enabled": true
}
EOF

# Create the distribution
echo "🚀 Creating CloudFront distribution..."
DISTRIBUTION_OUTPUT=$(aws cloudfront create-distribution --distribution-config file:///tmp/cloudfront-config.json)

DISTRIBUTION_ID=$(echo "$DISTRIBUTION_OUTPUT" | jq -r '.Distribution.Id')
DISTRIBUTION_DOMAIN=$(echo "$DISTRIBUTION_OUTPUT" | jq -r '.Distribution.DomainName')

echo "✅ CloudFront distribution created!"
echo "   Distribution ID: ${DISTRIBUTION_ID}"
echo "   Domain Name: ${DISTRIBUTION_DOMAIN}"

# Create CloudFront key pair for signed URLs
echo "🔐 Creating CloudFront key pair..."

# Generate RSA key pair
openssl genrsa -out cloudfront-private-key.pem 2048
openssl rsa -pubout -in cloudfront-private-key.pem -out cloudfront-public-key.pem

# Upload public key to CloudFront
PUBLIC_KEY_CONFIG=$(cat <<EOF
{
    "CallerReference": "${CALLER_REFERENCE}-key",
    "Name": "musify-streaming-key",
    "EncodedKey": "$(cat cloudfront-public-key.pem | sed '1d;$d' | tr -d '\n')",
    "Comment": "Key for Musify streaming signed URLs"
}
EOF
)

KEY_OUTPUT=$(aws cloudfront create-public-key --public-key-config "$PUBLIC_KEY_CONFIG")
KEY_ID=$(echo "$KEY_OUTPUT" | jq -r '.PublicKey.Id')

echo "✅ CloudFront key pair created!"
echo "   Key Pair ID: ${KEY_ID}"

# Create .env file entries
echo ""
echo "📝 Add these to your .env file:"
echo "================================"
cat <<EOF
# CloudFront Configuration
CDN_ENABLED=true
CDN_BASE_URL=https://${DISTRIBUTION_DOMAIN}
CLOUDFRONT_DOMAIN=${DISTRIBUTION_DOMAIN}
CLOUDFRONT_DISTRIBUTION_ID=${DISTRIBUTION_ID}
CLOUDFRONT_KEY_PAIR_ID=${KEY_ID}
CLOUDFRONT_PRIVATE_KEY_PATH=./cloudfront-private-key.pem

# S3 Configuration
S3_BUCKET_NAME=${S3_BUCKET_NAME}
EOF

echo ""
echo "⚠️  IMPORTANT:"
echo "1. Keep cloudfront-private-key.pem secure!"
echo "2. The distribution will take 15-20 minutes to deploy globally"
echo "3. Test the CDN URL after deployment is complete"
echo ""
echo "🎉 Setup complete!"

# Cleanup temp files
rm -f /tmp/bucket-policy.json /tmp/cors.json /tmp/cloudfront-config.json cloudfront-public-key.pem