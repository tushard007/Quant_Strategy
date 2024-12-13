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
