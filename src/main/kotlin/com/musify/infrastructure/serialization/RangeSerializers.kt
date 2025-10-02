package com.musify.infrastructure.serialization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@OptIn(ExperimentalSerializationApi::class)
object IntRangeSerializer : KSerializer<IntRange> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IntRange") {
        element<Int>("start")
        element<Int>("endInclusive")
    }

    override fun serialize(encoder: Encoder, value: IntRange) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.first)
            encodeIntElement(descriptor, 1, value.last)
        }
    }

    override fun deserialize(decoder: Decoder): IntRange {
        return decoder.decodeStructure(descriptor) {
            var start = 0
            var end = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> start = decodeIntElement(descriptor, 0)
                    1 -> end = decodeIntElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            start..end
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
object DoubleRangeSerializer : KSerializer<ClosedFloatingPointRange<Double>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("DoubleRange") {
        element<Double>("start")
        element<Double>("endInclusive")
    }

    override fun serialize(encoder: Encoder, value: ClosedFloatingPointRange<Double>) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.start)
            encodeDoubleElement(descriptor, 1, value.endInclusive)
        }
    }

    override fun deserialize(decoder: Decoder): ClosedFloatingPointRange<Double> {
        return decoder.decodeStructure(descriptor) {
            var start = 0.0
            var end = 0.0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> start = decodeDoubleElement(descriptor, 0)
                    1 -> end = decodeDoubleElement(descriptor, 1)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            start..end
        }
    }
}