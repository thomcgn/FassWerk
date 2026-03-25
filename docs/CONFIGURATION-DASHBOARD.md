# Konfigurationsdashboard - Sales Tracking

## Übersicht

Das Konfigurationsdashboard ermöglicht es Ihnen, alle Parameter der Nachbestellberechnung über eine benutzerfreundliche Web-Oberfläche zu verwalten.

**Zugriff:** `/sales-configuration`

## Funktionen

### 1. Global Einstellungen

Zentrale Konfiguration der Standard-Parameter für alle Lagerartikel.

#### Parameter:

**📊 Lookback-Periode (Wochen)**
- **Beschreibung:** Wie lange ist der Zeitraum für die Durchschnittsberechnung?
- **Standard:** 4 Wochen
- **Bereich:** 1-52 Wochen
- **Auswirkung:**
  - `< 4 Wochen`: Schneller reagierend, anfälliger für Schwankungen
  - `= 4 Wochen`: Balanciert, empfohlen
  - `> 4 Wochen`: Glättet Spitzen, ignoriert kurzfristige Trends

**🛡️ Sicherheitsbestands-Faktor**
- **Beschreibung:** Multiplikator für den Puffer gegen unvorhersehbare Verkaufsspitzen
- **Standard:** 1.5
- **Bereich:** 0.5 - 5.0
- **Berechnung:** Sicherheitsbestand = Wöchentlicher Umsatz × Faktor
- **Beispiele:**
  - `1.0`: Minimal - Genau der wöchentliche Umsatz
  - `1.5`: Standard - 50% Puffer
  - `2.0`: Höher - 100% Puffer für volatile Getränke
  - `3.0+`: Maximum - Für sehr unsichere Artikel

**⏱️ Standard-Lieferzeitraum (Tage)**
- **Beschreibung:** Typische Lieferdauer vom Lieferanten
- **Standard:** 3 Tage
- **Bereich:** 1-30 Tage
- **Berechnung:** Lead-Time Wochen = Tage ÷ 7
- **Beispiele:**
  - `1 Tag`: Schnelle lokale Lieferanten
  - `3 Tage`: Standard-Lieferanten
  - `7+ Tage`: Ausland oder spezielle Lieferanten

### 2. Pro-Artikel Konfiguration

Spezialisierte Einstellungen für individuelle Lagerartikel, die die Global Einstellungen überschreiben.

**Wann verwenden:**
- ✓ Getränke mit unregelmäßigen Umsatzmustern
- ✓ Unterschiedliche Lieferzeiten je Lieferant
- ✓ Saisonale Getränke
- ✓ Neue Getränke (zu kurze Historie)

**Verwaltung:**
1. Tab "Pro Artikel" öffnen
2. Auf einen Lagerartikel klicken
3. "Bearbeiten" oder "Konfigurieren" Taste drücken
4. Parameter anpassen
5. "Speichern" drücken

**Status-Anzeige:**
- 🟢 **Konfiguriert**: Artikel hat eigene Parameter
- ⚪ **Standard**: Verwendet Global Einstellungen

## Berechnungsbeispiel

**Angenommen:**
- Wöchentlicher Durchschnittsumsatz: 2.000 ml
- Aktueller Bestand: 500 ml
- Lieferzeitraum: 3 Tage
- Sicherheits-Faktor: 1.5

**Berechnung:**
```
Lead-Time Verbrauch = 2.000 × (3 ÷ 7) = 857 ml
Sicherheitsbestand = 2.000 × 1.5 = 3.000 ml

Empfohlene Nachbestellmenge = 857 + 3.000 - 500 = 3.357 ml
```

**Das Dashboard zeigt diese Berechnung live an!**

## Auswirkungen von Parameteränderungen

### Szenario: Erhöhung der Lookback-Periode

```
Von: 4 Wochen
Zu:  8 Wochen

Effekt: Durchschnitt wird stabiler, weniger anfällig für Schwankungen
        → Kleinere Nachbestellmengen bei temporären Spitzen
        → Bessere bei konstanten Umsatzen
```

### Szenario: Erhöhung des Sicherheits-Faktors

```
Von: 1.5×
Zu:  2.0×

Effekt: Größerer Puffer = Höhere Nachbestellmengen
        → Weniger Risiko von Engpässen
        → Mehr Lagerfläche benötigt
        → Höhere Kapitalbindung
```

### Szenario: Verkürzung des Lieferzeitraums

```
Von: 3 Tage
Zu:  1 Tag

Effekt: Kleinere Nachbestellmengen
        → Weniger Lagerbestand notwendig
        → Schneller auf Trends reagieren
        → Mehr häufigere Bestellungen
```

## Best Practices

### 1. Globale Parameter wählen
- Beginnen Sie mit Durchschnittswerten
- 4 Wochen Lookback für die meisten Getränke
- 1.5× Sicherheits-Faktor als Standard
- Lieferzeitraum basierend auf Ihrem Hauptlieferanten

### 2. Pro-Artikel Anpassungen
- **Hochfrequente Getränke**: Kürzerer Lookback (2-3 Wochen)
- **Saisonale Getränke**: Längerer Lookback (6-8 Wochen)
- **Ungeklärte Getränke**: Höherer Sicherheits-Faktor (2.0+)
- **Verschiedene Lieferanten**: Individuelle Lead-Times

### 3. Monitoring
- Überprüfen Sie monatlich ob die Nachbestellmengen sinnvoll sind
- Beobachten Sie die "Wochen bis Stockout" Metrik
- Passen Sie an wenn zu häufig oder zu selten bestellt wird

### 4. Testing
- Ändern Sie nur einen Parameter auf einmal
- Beobachten Sie die Auswirkungen über 1-2 Wochen
- Reverting wenn die Ergebnisse schlechter werden

## Häufig gestellte Fragen

**F: Wann sollte ich den Lieferzeitraum ändern?**
A: Wenn sich der Lieferant ändert oder die typische Lieferdauer unterschiedlich wird. Machen Sie dies pro Artikel in der spezifischen Konfiguration.

**F: Mein Getränk wird ständig knapp. Was sollte ich ändern?**
A: Erhöhen Sie entweder:
- Den Sicherheits-Faktor (z.B. auf 2.0)
- Den Lieferzeitraum (wenn Lieferant länger braucht)
- Die Lookback-Periode (um schneller auf Trends zu reagieren)

**F: Ich habe zu viel Lagerbestand. Was sollte ich ändern?**
A: Verringern Sie entweder:
- Den Sicherheits-Faktor (z.B. auf 1.0)
- Die Lookback-Periode (schneller reagieren)
- Den Lieferzeitraum (wenn Lieferant schneller liefert)

**F: Wie oft sollte ich die Konfiguration überprüfen?**
A: Mindestens monatlich. Die System führt tägliche automatische Berechnungen durch, aber Sie sollten manuell überprüfen ob die Parameter noch sinnvoll sind.

**F: Kann ich die Änderungen rückgängig machen?**
A: Ja, Sie können jederzeit "Zurücksetzen" klicken um zur letzten gespeicherten Version zurückzukehren.

## API-Integration

### Globale Einstellungen abrufen
```bash
GET /api/inventory/configuration
```

Response:
```json
{
  "weeksLookback": 4,
  "defaultSafetyFactor": 1.5,
  "defaultLeadTimeDays": 3
}
```

### Globale Einstellungen aktualisieren
```bash
PUT /api/inventory/configuration
Content-Type: application/json

{
  "weeksLookback": 6,
  "defaultSafetyFactor": 2.0,
  "defaultLeadTimeDays": 4
}
```

### Pro-Artikel Konfiguration abrufen
```bash
GET /api/inventory/{id}/consumption-metadata
```

### Pro-Artikel Konfiguration aktualisieren
```bash
PUT /api/inventory/{id}/consumption-metadata
Content-Type: application/json

{
  "leadTimeDays": 5,
  "safetyStockFactor": 2.0,
  "weeksLookback": 8
}
```

## Automatische Berechnung

Die Nachbestellmengen werden automatisch neu berechnet:
- **Täglich um 02:00 UTC** (vollständige Neuberechnung)
- **Bei jedem Verkauf** (unmittelbare Aktualisierung für den Artikel)

Änderungen in diesem Dashboard werden sofort aktiv!

## Tipps für optimale Ergebnisse

1. **Start mit Standard**: Nutzen Sie zunächst die Default-Werte
2. **Beobachten**: Überwachen Sie die Nachbestellmengen 1-2 Wochen
3. **Anpassen**: Basierend auf Ihre Beobachtungen anpassen
4. **Iterieren**: Kleine Änderungen zu einer Zeit
5. **Dokumentieren**: Notieren Sie warum Sie änderungen vorgenommen haben

## Support

Bei Fragen konsultieren Sie:
- `/docs/sales-tracking-reorder.md` - Technische Dokumentation
- `/docs/QUICKSTART-sales-tracking.md` - Schnellstart
- `/IMPLEMENTATION_SUMMARY.md` - Detaillierte Implementierung

