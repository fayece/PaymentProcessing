package nl.fayece.paymentprocessing.domain.exceptions

class InsufficientBalanceException(iban: String) : RuntimeException("Insufficient balance for $iban")
