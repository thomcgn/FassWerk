# Sales Tracking and Reorder Calculation System

## Übersicht

Dieses System ermöglicht es, Getränkeverkäufe automatisch zu erfassen und basierend auf dem Wochenumsatz die empfohlene Nachbestellmenge zu berechnen.

## Funktionsweise

### 1. **Verkaufsverfolgung (DrinkSalesTrackingService)**

Wenn ein Getränk verkauft wird, wird automatisch:
- Die täglich verkaufte Menge erfasst (in `drink_sales_daily`)
- Diese werden täglich um 1:00 Uhr UTC zu wöchentlichen Aggregaten zusammengefasst (in `drink_sales_weekly`)

### 2. **Nachbestellberechnung (ReorderCalculationService)**

Die empfohlene Nachbestellmenge wird **täglich um 2:00 Uhr UTC** automatisch berechnet basierend auf:

**Formel:**
```
Empfohlene Menge = (Wöchentlicher Durchschnitt × Lead-Time Wochen) + Sicherheitsbestand - Aktueller Bestand
```

Wobei:
- **Wöchentlicher Durchschnitt**: Durchschnittliche wöchentliche Verbrauchsmenge (aus letzten 4 Wochen, konfigurierbar)
- **Lead-Time**: Lieferzeitraum des Lieferanten (Standard: 3 Tage, konfigurierbar pro Artikel)
- **Sicherheitsbestand**: Wöchentlicher Verbrauch × 1,5 (Faktor konfigurierbar)
- **Aktueller Bestand**: Lagerbestand des Artikels

### 3. **Automatische Integration**

Beim Abschluss von Bestellungen:
1. **Lagerabbuchung**: Der Bestand wird angepasst
2. **Verkaufsverfolgung**: Die Menge wird in `DrinkSalesDaily` erfasst
3. **Berechnung**: Die Nachbestellmenge wird aktualisiert

## Konfiguration

In `application.yml`:

```yaml
app:
  sales:
    calculation:
      weeks-lookback: 4                    # Wochen für Durchschnittsberechnung
      default-safety-factor: 1.5           # Sicherheitsbestands-Multiplikator
      default-lead-time-days: 3            # Lead-Time in Tagen
```

### Pro-Artikel Konfiguration

Über die `ConsumptionMetadata`-Tabelle können diese Werte pro Lagerartikel überschrieben werden:

```json
{
  "leadTimeDays": 5,
  "safetyStockFactor": 2.0,
  "weeksLookback": 8
}
```

## API-Endpoints

### Verkaufsanalyse

**Tägliche Verkäufe abrufen:**
```
GET /api/inventory/sales/daily?startDate=2024-01-01&endDate=2024-01-31
```

Response:
```json
[
  {
    "id": 1,
    "drinkId": 5,
    "drinkName": "Heineken",
    "drinkVariantId": 12,
    "drinkVariantName": "0.5L Flasche",
    "saleDate": "2024-01-01",
    "quantitySold": 25,
    "volumeSoldMl": 12500
  }
]
```

**Wöchentliche Verkäufe abrufen:**
```
GET /api/inventory/sales/weekly/{variantId}?weeks=4
```

Response:
```json
[
  {
    "id": 10,
    "drinkId": 5,
    "drinkName": "Heineken",
    "drinkVariantId": 12,
    "drinkVariantName": "0.5L Flasche",
    "weekStartDate": "2024-01-01",
    "quantitySold": 175,
    "volumeSoldMl": 87500,
    "averageDailyQuantity": 25.0000,
    "averageDailyVolumeMl": 12500.0000
  }
]
```

### Nachbestellberechnung

**Aktuelle Berechnung abrufen:**
```
GET /api/inventory/{id}/reorder-calculation
```

Response:
```json
{
  "id": 99,
  "inventoryItemId": 15,
  "inventoryItemName": "Heineken Fässer",
  "calculationDate": "2024-01-25T02:00:00Z",
  "currentStockAmount": 2500,
  "weeklyAverageConsumption": 12500,
  "recommendedReorderAmount": 28750,
  "isBelowThreshold": false,
  "weeksUntilStockout": 0.20
}
```

**Manuelle Neuberechnung:**
```
POST /api/inventory/{id}/calculate-reorder
```

**Artikel unterhalb des Schwellwerts:**
```
GET /api/inventory/reorder-calculations/below-threshold
```

**Berechnungsverlauf:**
```
GET /api/inventory/{id}/reorder-calculations/history?limit=10
```

### Konsumptions-Metadaten

**Metadaten abrufen und aktualisieren:**
```
PUT /api/inventory/{id}/consumption-metadata
```

Request:
```json
{
  "leadTimeDays": 5,
  "safetyStockFactor": 2.0,
  "weeksLookback": 8
}
```

## Datenbank-Schema

### `drink_sales_daily`
Tägliche Verkaufsaggregate pro Getränk/Variante.

```sql
CREATE TABLE drink_sales_daily (
  id BIGSERIAL PRIMARY KEY,
  drink_id BIGINT NOT NULL,
  drink_variant_id BIGINT,
  sale_date DATE NOT NULL,
  quantity_sold NUMERIC(12,2),
  volume_sold_ml NUMERIC(14,2),
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
);
```

### `drink_sales_weekly`
Wöchentliche Aggregierte Daten mit Durchschnitten.

```sql
CREATE TABLE drink_sales_weekly (
  id BIGSERIAL PRIMARY KEY,
  drink_id BIGINT NOT NULL,
  drink_variant_id BIGINT,
  week_start_date DATE NOT NULL,
  quantity_sold NUMERIC(12,2),
  volume_sold_ml NUMERIC(14,2),
  average_daily_quantity NUMERIC(12,4),
  average_daily_volume_ml NUMERIC(14,4),
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
);
```

### `reorder_calculations`
Berechnete Nachbestellempfehlungen mit Zeitstempel.

```sql
CREATE TABLE reorder_calculations (
  id BIGSERIAL PRIMARY KEY,
  inventory_item_id BIGINT NOT NULL,
  calculation_date TIMESTAMPTZ,
  current_stock_amount NUMERIC(14,2),
  weekly_average_consumption NUMERIC(12,4),
  recommended_reorder_amount NUMERIC(14,2),
  is_below_threshold BOOLEAN,
  weeks_until_stockout NUMERIC(6,2),
  created_at TIMESTAMPTZ
);
```

### `consumption_metadata`
Konfigurierte Parameter pro Lagerartikel.

```sql
CREATE TABLE consumption_metadata (
  id BIGSERIAL PRIMARY KEY,
  inventory_item_id BIGINT UNIQUE NOT NULL,
  lead_time_days INTEGER DEFAULT 3,
  safety_stock_factor NUMERIC(4,2) DEFAULT 1.5,
  weeks_lookback INTEGER DEFAULT 4,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
);
```

## Scheduled Tasks

1. **Täglich um 1:00 Uhr UTC**: Aggregation von täglichen zu wöchentlichen Daten
   - Methode: `DrinkSalesTrackingService.aggregateDailyToWeekly()`

2. **Täglich um 2:00 Uhr UTC**: Neuberechnung aller Nachbestellmengen
   - Methode: `ReorderCalculationService.recalculateAllInventoryItems()`

## Beispiel-Workflow

1. **Kunde bestellt ein Getränk** (z.B. 0,5L Heineken)
   - `TableOrderItem` wird erstellt mit `quantity=1, deductedVolumeMl=500`

2. **Bestellung wird abgeschlossen**
   - `InventoryService.deductForOrderItem()` wird aufgerufen
   - Bestand wird aktualisiert: `-500 ml`
   - `DrinkSalesTrackingService.recordSale()` wird aufgerufen
   - Tägliches Verkaufsaggregat wird aktualisiert
   - `ReorderCalculationService.calculateReorderAmount()` wird aufgerufen
   - Empfohlene Nachbestellmenge wird aktualisiert

3. **Nachts um 1:00 Uhr**
   - Tägliche Daten werden zu wöchentlichen Aggregaten zusammengefasst

4. **Nachts um 2:00 Uhr**
   - Alle Nachbestellmengen werden neu berechnet

## Integration ins Frontend

Die Bar Admin-Seite kann erweitert werden um:

- Dashboard mit Verbrauchstrends
- Wöchentliche Verkaufsstatistiken pro Getränk
- Automatische Nachbestellungen für Artikel unter dem Schwellwert
- Graphische Darstellung der Konsumrate
- Warenliste mit Nachbestellstatus

Siehe `bar-admin-client.tsx` für mögliche UI-Erweiterungen.

