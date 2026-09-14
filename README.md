# Quant Strategy  

## Overview  
This project focuses on the analysis and evaluation of different quantitative trading strategies, including their performance across varying timeframes. The objective is to gain insights into strategy effectiveness, identify profitable patterns, and refine methods for algorithmic trading.  

## Features  
- **Data Collection:** Fetching historical stock price data from Yahoo Finance.  
- **Data Storage:** Storing the fetched data in a PostgreSQL database for efficient retrieval and analysis.  
- **Strategy Implementation:** Implementing core quantitative trading strategies in Java using Spring Boot.  
- **Backtesting Framework:** Testing strategies against historical data to evaluate their performance.  
- **Timeframe Analysis:** Comparing strategy performance over different time intervals to understand their robustness and adaptability.  

## Technical Stack  
- **Python:**  
  - Used for data extraction and pre-processing.  
  - Fetches stock price data from Yahoo Finance.  
  - Manages data insertion into the PostgreSQL database.  
- **PostgreSQL:**  
  - Serves as the centralized database to store and manage historical stock data.  
  - Optimized for large-scale financial data storage and retrieval.  
- **Java with Spring Boot:**  
  - Implements the core quantitative strategies.  
  - Provides a scalable framework for backtesting and simulating strategy outcomes.  

## Project Goals  
- Develop a reusable and modular framework for testing quantitative trading strategies.  
- Compare strategies on metrics such as return, risk, and Sharpe ratio over different time horizons.  
- Enable easy integration of new strategies and data sources for future enhancements.  

## Future Enhancements   
- Visualization tools for better insight into strategy performance.  

## Cloud Run container releases

Cloud Run requires `linux/amd64`, including when building on an ARM64 Mac.
Build and push with an explicit platform and a new tag for each release:

```sh
IMAGE="docker.io/tushardesarda/quant-strategy:cloudrun-$(date -u +%Y%m%d-%H%M%S)"
docker buildx build --platform linux/amd64 --tag "$IMAGE" --push .
docker buildx imagetools inspect "$IMAGE"
```

Authenticate with `docker login` before pushing. Verify that the published
manifest includes `linux/amd64`, then deploy that exact image tag to the existing
Cloud Run service, retaining its production environment and database settings.
An OCI image index is supported when it includes `linux/amd64`; an ARM-only index
cannot run on Cloud Run. Use a fresh release tag instead of reusing `latest`.

The existing `cloudbuild.yaml` deploys to App Engine, not Cloud Run.

For stock/ETF chunked imports, persistent progress, and Cloud Run Job deployment,
see [Price imports](docs/price-imports.md).

## Authentication and roles

Momentum Dashboard is public. Accounts sign in with email addresses. Administrators have all menus and an Administration → Users screen to create accounts, assign Admin/User roles, reset passwords, and disable access. Users have Analysis menus. Password hashes and the automatically generated JWT signing key are persisted in the database. See [JWT/RBAC setup and API usage](docs/JWT_RBAC.md) for the one-time first-admin setup and migration instructions.
