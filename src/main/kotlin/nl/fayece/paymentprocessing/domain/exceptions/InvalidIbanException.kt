package nl.fayece.paymentprocessing.domain.exceptions

class InvalidIbanException(iban: String, reason: String) : RuntimeException("Invalid IBAN: '$iban': $reason")
