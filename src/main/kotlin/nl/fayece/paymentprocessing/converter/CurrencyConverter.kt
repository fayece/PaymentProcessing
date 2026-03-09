package nl.fayece.paymentprocessing.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.Currency

@Converter(autoApply = true)
class CurrencyConverter : AttributeConverter<Currency, String> {

    override fun convertToDatabaseColumn(currency: Currency): String =
        currency.currencyCode

    override fun convertToEntityAttribute(code: String): Currency =
        Currency.getInstance(code)
}
