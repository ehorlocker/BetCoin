package com.betcoin.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for [RecoveryCode]. */
class RecoveryCodeTest {

  // --- generate ---

  @Test
  fun generate_isFormattedAsThreeGroupsOfFour() {
    repeat(200) {
      assertThat(RecoveryCode.generate()).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")
    }
  }

  @Test
  fun generate_usesOnlyTheUnambiguousAlphabet() {
    val seen = mutableSetOf<Char>()
    repeat(2000) { seen += RecoveryCode.generate().replace("-", "").toSet() }

    assertThat(seen).containsNoneOf('0', 'O', '1', 'I')
    assertThat(seen).containsExactlyElementsIn(RecoveryCode.ALPHABET.toSet())
  }

  @Test
  fun generate_returnsDifferentCodes() {
    val codes = List(100) { RecoveryCode.generate() }

    assertThat(codes.toSet()).hasSize(100)
  }

  @Test
  fun alphabet_isUppercaseLettersAndDigitsWithoutAmbiguousOnes() {
    val expected = (('A'..'Z') + ('2'..'9')).filterNot { it in "0O1I" }

    assertThat(RecoveryCode.ALPHABET.toList()).containsExactlyElementsIn(expected).inOrder()
    assertThat(RecoveryCode.LENGTH).isEqualTo(12)
  }

  // --- normalize ---

  @Test
  fun normalize_formattedCode_stripsDashes() {
    assertThat(RecoveryCode.normalize("ABCD-EFGH-JK23")).isEqualTo("ABCDEFGHJK23")
  }

  @Test
  fun normalize_lowercaseWithoutDashes_uppercases() {
    assertThat(RecoveryCode.normalize("abcdefghjk23")).isEqualTo("ABCDEFGHJK23")
  }

  @Test
  fun normalize_mixedCaseWithSpaces_isAccepted() {
    assertThat(RecoveryCode.normalize(" abCD efGH-jk23 ")).isEqualTo("ABCDEFGHJK23")
  }

  @Test
  fun normalize_wrongLength_returnsNull() {
    assertThat(RecoveryCode.normalize("ABCD-EFGH-JK2")).isNull()
    assertThat(RecoveryCode.normalize("ABCD-EFGH-JK234")).isNull()
    assertThat(RecoveryCode.normalize("")).isNull()
  }

  @Test
  fun normalize_charactersOutsideAlphabet_returnsNull() {
    assertThat(RecoveryCode.normalize("ABCD-EFGH-JK20")).isNull()
    assertThat(RecoveryCode.normalize("ABCD-EFGH-JKO2")).isNull()
    assertThat(RecoveryCode.normalize("ABCD-EFGH-JK!2")).isNull()
  }

  @Test
  fun normalize_generatedCode_roundTrips() {
    val code = RecoveryCode.generate()

    assertThat(RecoveryCode.normalize(code)).isEqualTo(code.replace("-", ""))
  }
}
