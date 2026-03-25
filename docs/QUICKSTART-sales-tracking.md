# Sales Tracking und Reorder Berechnung - Schnellstart

## Installation

Das System wird automatisch bei der Datenbankinitialisierung durch die Migration `V14__drink_sales_tracking.sql` eingerichtet.

## Automatische Funktionen

### 1. **Verkaufsverfolgung**
Jedes Mal wenn ein Getränk verkauft wird (Bestellabschluss):
- Verkaufsmenge wird in `drink_sales_daily` erfasst
- Lagerbestand wird aktualisiert

### 2. **Tägliche Aggregation**
**Zeitpunkt:** Täglich um 1:00 Uhr UTC

Tägliche Verkäufe werden zu wöchentlichen Aggregaten zusammengefasst:
- Summe der Verkäufe pro Woche
- Durchschnittliche tägliche Verbrauchsmenge

### 3. **Nachbestellberechnung**
**Zeitpunkt:** Täglich um 2:00 Uhr UTC

Für jeden aktiven Lagerartikel:
- Empfohlene Nachbestellmenge wird berechnet
- Status (unterhalb Schwellwert) wird aktualisiert
- `inventory_items.recommended_reorder_amount` wird aktualisiert

## Konfiguration

### Global (application.yml)

```yaml
app:
  sales:
    calculation:
      weeks-lookback: 4              # Wochen für Durchschnittsberechnung
      default-safety-factor: 1.5     # Sicherheitsbestands-Multiplikator
      default-lead-time-days: 3      # Lead-Time in Tagen
```

### Pro Artikel

PUT `/api/inventory/{id}/consumption-metadata`

```json
{
  "leadTimeDays": 5,
  "safetyStockFactor": 2.0,
  "weeksLookback": 8
}
```

## API-Nutzung

### Verkaufsstatistiken

```bash
# Tägliche Verkäufe
curl "http://localhost:8080/api/inventory/sales/daily?startDate=2024-01-01&endDate=2024-01-31"

# Wöchentliche Verkäufe
curl "http://localhost:8080/api/inventory/sales/weekly/12?weeks=4"
```

### Nachbestellberechnung

```bash
# Aktuelle Berechnung abrufen
curl "http://localhost:8080/api/inventory/15/reorder-calculation"

# Artikel unterhalb Schwellwert
curl "http://localhost:8080/api/inventory/reorder-calculations/below-threshold"

# Berechnungsverlauf
curl "http://localhost:8080/api/inventory/15/reorder-calculations/history?limit=10"
```

## Frontend-Integration

### Komponenten

`/frontend/components/reorder-calculation.tsx` enthält:

1. **ReorderCalculationPanel**
   - Zeigt aktuelle Berechnung für einen Artikel
   - Zeigt Warnung wenn unter Schwellwert

2. **CriticalReorderItems**
   - Dashboard-Widget für kritische Artikel
   - Listet alle Artikel mit zu niedrigem Bestand

### Beispiel-Integration in bar-admin

```tsx
import { ReorderCalculationPanel, CriticalReorderItems } from "@/components/reorder-calculation";

export default function BarAdminClient() {
  // ...
  return (
    <section>
      {/* Bestehender Code */}
      
      {/* Neue Komponenten */}
      <CriticalReorderItems />
      
      {/* Für jeden Lagerartikel */}
      {inventoryItems.map(item => (
        <ReorderCalculationPanel key={item.id} inventoryItem={item} />
      ))}
    </section>
  );
}
```

## Beispiel-Szenario

**Situation:**
- Heineken 0.5L Flasche: Aktueller Bestand = 500 ml
- Durchschnittlicher Wochenumsatz = 2.000 ml
- Lieferzeitraum (Lead-Time) = 3 Tage (0,43 Wochen)
- Sicherheitsbestand-Faktor = 1.5

**Berechnung:**
```
Lead-Time Verbrauch = 2.000 × 0,43 = 860 ml
Sicherheitsbestand = 2.000 × 1.5 = 3.000 ml
Empfohlene Menge = (860 + 3.000) - 500 = 3.360 ml
```

**Ergebnis:** Es sollten 3.360 ml nachbestellt werden.

## Troubleshooting

### Verkäufe werden nicht erfasst
- Prüfe ob `LinkedDrinkVariant` auf InventoryItem konfiguriert ist
- Logs prüfen für Fehler in `recordSaleAndUpdateReorder`

### Nachbestellmenge ist 0
- Möglich wenn: Bestand > Schwellwert UND kein Verbrauch in letzten Wochen
- Prüfe ob Verkäufe korrekt erfasst werden

### Berechnungen laufen nicht
- Prüfe ob Scheduled Tasks aktiviert sind (`@EnableScheduling` in Boot-App)
- Prüfe Timezone-Konfiguration (Standard: UTC)
- Aktivität im Log um 1:00 Uhr und 2:00 Uhr UTC prüfen

## Performance

- `drink_sales_daily` und `drink_sales_weekly` haben Indizes auf häufig abgefragte Spalten
- `ReorderCalculation` speichert nur die neuesten Berechnungen (tägliche Rollups möglich)
- Für große Mengen an Verkaufsdaten: `DrinkSalesDaily` möglicherweise partitionieren

## Weitere Entwicklung

Mögliche Erweiterungen:
- Prognosen für zukünftige Verkäufe (Linear Regression)
- Saisonale Anpassungen
- Machine Learning für optimale Bestellmengen
- Integration mit Lieferanten-APIs für automatische Bestellungen
- SMS/Email-Alarme für kritische Bestände

