package com.hades.safepath.auth

object PhoneNumberNormalizer {

  private val e164Regex = Regex("^\\+[1-9]\\d{7,14}$")

  /**
   * Normalizes user input to E.164.
   * Accepts Sri Lanka local numbers (07xxxxxxxx) and converts to +94...
   */
  fun normalize(raw: String): String? {
    val digitsOnly = raw.trim().filter { it.isDigit() || it == '+' }
    val candidate = when {
      digitsOnly.startsWith("+") -> digitsOnly
      digitsOnly.startsWith("94") -> "+$digitsOnly"
      digitsOnly.startsWith("0") -> "+94${digitsOnly.drop(1)}"
      digitsOnly.length in 9..10 -> "+94$digitsOnly"
      else -> "+$digitsOnly"
    }
    return candidate.takeIf { e164Regex.matches(it) }
  }
}
