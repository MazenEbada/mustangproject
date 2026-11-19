# OpenPEPPOL BIS Billing 3.0 – Regeln 3.0.22 (CUSTOM:TEK/MEB:19.11.2026)

Quelle: https://github.com/OpenPEPPOL/peppol-bis-invoice-3, Tag `3.0.22`, `rules/sch/PEPPOL-EN16931-UBL.sch`
("reproduced with permission from CEN"). Das Repo hat keine eigene Lizenzdatei. Vor der kommerziellen
Auslieferung die Nutzungsbedingungen bei OpenPEPPOL klären.

Kompiliert mit Saxon-HE 12.10 und dem ISO-Skeleton (ph-schematron-xslt 8.0.6, `20100710-xslt2`):
iso_dsdl_include.xsl → iso_abstract_expand.xsl → iso_svrl_for_xslt2.xsl (`allow-foreign=true`).

Wird nur angewendet mit `--custom-action VALIDATE --peppol` (bzw. `ZUGFeRDValidator.enablePeppolRules()`)
und nur auf UBL, deren CustomizationID PEPPOL nennt.
