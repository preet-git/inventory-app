package com.ablsoft.inventory.model;

/** A row that did not make it in, and why. The API reports the number; the reason is stored. */
public record RejectedRow(int rowNumber, String reason) {
}
