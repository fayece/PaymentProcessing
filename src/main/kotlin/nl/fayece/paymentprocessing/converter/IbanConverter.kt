package nl.fayece.paymentprocessing.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import nl.fayece.paymentprocessing.domain.Iban

@Converter(autoApply = true)
class IbanConverter : AttributeConverter<Iban, String> {

    override fun convertToDatabaseColumn(iban: Iban): String = iban.value
    override fun convertToEntityAttribute(value: String): Iban = Iban(value)
}
