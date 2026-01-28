package com.musify.core.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import com.musify.infrastructure.serialization.LocalDateTimeSerializer

object IntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IntRange") {
        element("start", PrimitiveSerialDescriptor("start", PrimitiveKind.INT))
        element("endInclusive", PrimitiveSerialDescriptor("endInclusive", PrimitiveKind.INT))
    }
    
    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.start)
            encodeIntElement(descriptor, 1, value.endInclusive)
        }
    }
    
    override fun deserialize(decoder: Decoder): IntRange {
        return decoder.decodeStructure(descriptor) {
            var start = 0
            var endInclusive = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> start = decodeIntElement(descriptor, 0)
                    1 -> endInclusive = decodeIntElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            IntRange(start, endInclusive)
        }
    }
}