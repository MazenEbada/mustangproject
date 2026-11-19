package org.mustangproject;

import java.math.BigDecimal;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * CUSTOM:AMC/MEB:17.11.2026 - Geleistete Anzahlung/Abschlagszahlung (nur ZUGFeRD EXTENDED):
 * ram:ApplicableHeaderTradeSettlement/ram:SpecifiedAdvancePayment.
 *
 * <p>Die EN16931 kennt nur den Gesamtbetrag (BT-113 TotalPrepaidAmount, wird ueber
 * {@link Invoice#setTotalPrepaidAmount(BigDecimal)} gesetzt); EXTENDED schluesselt die
 * einzelnen Zahlungen mit Steuer und Bezug auf die Anzahlungsrechnung auf. Upstream
 * kennt das Element nicht.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdvancePayment {

	/** Gezahlter Betrag inkl. Steuer (ram:PaidAmount). */
	protected BigDecimal paidAmount;
	/** Steuerbetrag der Zahlung (ram:IncludedTradeTax/ram:CalculatedAmount). */
	protected BigDecimal taxAmount;
	/** Steuerkategorie (S, Z, E, AE, K, G, ...), Standard S. */
	protected String taxCategoryCode = "S";
	/** Steuersatz in Prozent. */
	protected BigDecimal taxPercent;
	/** Nummer der Anzahlungs-/Abschlagsrechnung. */
	protected String invoiceID;
	/** Datum der Anzahlungs-/Abschlagsrechnung. */
	protected Date invoiceDate;

	public BigDecimal getPaidAmount() {
		return paidAmount;
	}

	public AdvancePayment setPaidAmount(BigDecimal paidAmount) {
		this.paidAmount = paidAmount;
		return this;
	}

	public BigDecimal getTaxAmount() {
		return taxAmount;
	}

	public AdvancePayment setTaxAmount(BigDecimal taxAmount) {
		this.taxAmount = taxAmount;
		return this;
	}

	public String getTaxCategoryCode() {
		return taxCategoryCode;
	}

	public AdvancePayment setTaxCategoryCode(String taxCategoryCode) {
		this.taxCategoryCode = taxCategoryCode;
		return this;
	}

	public BigDecimal getTaxPercent() {
		return taxPercent;
	}

	public AdvancePayment setTaxPercent(BigDecimal taxPercent) {
		this.taxPercent = taxPercent;
		return this;
	}

	public String getInvoiceID() {
		return invoiceID;
	}

	public AdvancePayment setInvoiceID(String invoiceID) {
		this.invoiceID = invoiceID;
		return this;
	}

	public Date getInvoiceDate() {
		return invoiceDate;
	}

	public AdvancePayment setInvoiceDate(Date invoiceDate) {
		this.invoiceDate = invoiceDate;
		return this;
	}
}
