package me.nimd.etbridge.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * ETLab's API is loosely typed: numbers arrive as strings ("77%", "41.5"), ids are
 * sometimes numbers and sometimes strings, booleans can be 0/1. These serializers mirror
 * parseNumber / parsePercent from webapp/src/utils/format.ts so a malformed field never
 * fails a whole response.
 */

/** Double that accepts numbers, numeric strings and percentages ("77%"); falls back to 0. */
typealias LDouble = @Serializable(with = LenientDoubleSerializer::class) Double

/** Int that accepts numbers and numeric strings ("3", "3.0"); falls back to 0. */
typealias LInt = @Serializable(with = LenientIntSerializer::class) Int

/** Non-null String that accepts any primitive; null becomes "". */
typealias LString = @Serializable(with = LenientStringSerializer::class) String

/** Nullable String that accepts any primitive. */
typealias LStringN = @Serializable(with = LenientNullableStringSerializer::class) String?

/** Boolean that accepts true/false, 1/0, "true"/"1"/"yes". */
typealias LBool = @Serializable(with = LenientBooleanSerializer::class) Boolean

fun parseLenientDouble(raw: String?, fallback: Double = 0.0): Double {
    if (raw == null) return fallback
    // Mirror JS parseFloat: take the longest numeric prefix after stripping '%'.
    val cleaned = raw.replace("%", "").trim()
    cleaned.toDoubleOrNull()?.let { return if (it.isNaN()) fallback else it }
    val prefix = Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?").find(cleaned)?.value
    return prefix?.toDoubleOrNull() ?: fallback
}

private fun JsonElement.primitiveContentOrNull(): String? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> content
    is JsonArray, is JsonObject -> null
}

private fun Decoder.lenientElement(): JsonElement? =
    (this as? JsonDecoder)?.decodeJsonElement()

object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double {
        val el = decoder.lenientElement() ?: return decoder.decodeDouble()
        return parseLenientDouble(el.primitiveContentOrNull())
    }
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        val el = decoder.lenientElement() ?: return decoder.decodeInt()
        return parseLenientDouble(el.primitiveContentOrNull()).toInt()
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        val el = decoder.lenientElement() ?: return decoder.decodeString()
        return el.primitiveContentOrNull() ?: ""
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

@OptIn(ExperimentalSerializationApi::class)
object LenientNullableStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientNullableString", PrimitiveKind.STRING).nullable
    override fun deserialize(decoder: Decoder): String? {
        val el = decoder.lenientElement() ?: return decoder.decodeString()
        return el.primitiveContentOrNull()
    }
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        val el = decoder.lenientElement() ?: return decoder.decodeBoolean()
        return when (el.primitiveContentOrNull()?.trim()?.lowercase()) {
            "true", "1", "yes", "y" -> true
            else -> false
        }
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
