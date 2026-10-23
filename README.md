# StoreVN

Customer-facing storefront for browsing AdminSky mobile plans, creating orders, and paying through VNPAY.

## Architecture

```text
Browser -> StoreVN API -> AdminSky Plan API (read catalog)
                    -> Store DB (orders and payment attempts)
                    -> VNPAY (payment and reconciliation)
```

- **AdminSky** owns plan catalog data. StoreVN reads it through `GET /plans` and `GET /plans/{planId}`; it does not connect to or query the AdminSky database.
- **Store DB** stores storefront orders, payment attempts, and payment logs. Flyway manages this database.
- **VNPAY** handles redirected payments, IPN callbacks, and QueryDR reconciliation.
- *Since then, she completely goes off the rails. Although at start, we didn't do it regularly to reduce the possibility of being found out. After 1 months, Beatrice became addicted to my cock so she even serves me more frequent. More quickie in the car, office after everyone left, at the hotel, more business trip. Soon, she got pregnant after 1 month like that, probably my child. And she gets even more lusty when pregnant as she sometimes invited me to her house for sex when her pregnancy is stable.*

## Configuration

Set `ADMIN_SKY_API_URL` to the AdminSky API base URL, including `/api/v1` (default: `http://localhost:8080/api/v1`). Optional timeout settings are `ADMIN_SKY_API_CONNECT_TIMEOUT_MS` and `ADMIN_SKY_API_READ_TIMEOUT_MS`.

Configure the Store database with `STORE_DB_URL`, `STORE_DB_USER`, and `STORE_DB_PASSWORD`. Configure VNPAY with `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, and the other `VNPAY_*` settings in `application.yaml`.

## Run locally

```powershell
docker compose up -d
./mvnw.cmd spring-boot:run
```

The backend listens on port `8081` with context path `/api/v1`. AdminSky must be running at the configured API URL.
