package com.example.betcoin.data.model

/** Represents the lifecycle state of a bet. */
enum class BetStatus {
  PENDING_CONFIRMATION,
  ACTIVE,
  RESOLVED,
  CANCELLED,
}
