# -*- coding: utf-8 -*-
"""
Created on Fri Mar 21 12:24:19 2025

@author: admin
"""

import pandas as pd
import yfinance as yf
from datetime import datetime, timedelta
import pytz  # Import timezone library

def fetch_stock_data(ticker):
    """Fetch 1-year stock data from Yahoo Finance and store it in memory."""
    try:
        ticker_symbol = ticker + ".NS"
        print(f"Fetching data for {ticker_symbol} with daily interval for 1 year")
        
        stock = yf.Ticker(ticker_symbol)
        stock_data = stock.history(interval='1d', period='1y')  # Fetch daily data for 1 year
        
        if stock_data is None or stock_data.empty or 'Close' not in stock_data:
            print(f"No data available for {ticker_symbol}")
            return None
        
        # Ensure index is timezone-aware (in UTC)
        stock_data.index = stock_data.index.tz_convert('UTC')
        
        return stock_data['Close']
    except Exception as e:
        print(f"Error fetching data for {ticker}: {e}")
        return None

def get_nearest_price(stock_data, target_date):
    """Find the nearest available price for a given date."""
    if stock_data is None or stock_data.empty:
        return None

    # Ensure target_date is also timezone-aware (convert to UTC)
    target_date = pd.Timestamp(target_date).tz_localize('UTC')

    if target_date in stock_data.index:
        return stock_data.loc[target_date]
    else:
        return stock_data.asof(target_date)

def calculate_returns(ticker, stock_data):
    try:
        print(stock_data)
        if stock_data is None:
            print(f"Skipping return calculation for {ticker} due to missing data.")
            return [ticker, None, None, None]
        
        # Convert today and past dates to UTC
        today = datetime.today().replace(tzinfo=pytz.UTC).strftime('%Y-%m-%d')
        three_months_ago = (datetime.today() - timedelta(days=90)).replace(tzinfo=pytz.UTC).strftime('%Y-%m-%d')
        six_months_ago = (datetime.today() - timedelta(days=180)).replace(tzinfo=pytz.UTC).strftime('%Y-%m-%d')
        twelve_months_ago = (datetime.today() - timedelta(days=365)).replace(tzinfo=pytz.UTC).strftime('%Y-%m-%d')

        current_price = stock_data.iloc[-1] if not stock_data.empty else None
        print("CurrentPrice", current_price)
        three_months_price = get_nearest_price(stock_data, three_months_ago)
        six_months_price = get_nearest_price(stock_data, six_months_ago)
        twelve_months_price = get_nearest_price(stock_data, twelve_months_ago)

        three_months_return = ((current_price - three_months_price) / three_months_price * 100) if three_months_price else None
        six_months_return = ((current_price - six_months_price) / six_months_price * 100) if six_months_price else None
        twelve_months_return = ((current_price - twelve_months_price) / twelve_months_price * 100) if twelve_months_price else None

        return [ticker, three_months_return, six_months_return, twelve_months_return]
    except Exception as e:
        print(f"Error calculating returns for {ticker}: {e}")
        return [ticker, None, None, None]

# Read stock tickers from CSV
input_file = "tickers.csv"  # Ensure this file exists with a column 'Ticker'
output_file = "stock_returns.xlsx"

df = pd.read_csv(input_file)
print(df)
tickers = df['Ticker'].tolist()

# Fetch stock data for all tickers and calculate returns
results = []
for ticker in tickers:
    stock_data = fetch_stock_data(ticker)
    results.append(calculate_returns(ticker, stock_data))

# Save results to Excel
output_df = pd.DataFrame(results, columns=["Ticker", "3M Return (%)", "6M Return (%)", "12M Return (%)"])
output_df.to_excel(output_file, index=False)

print(f"Stock returns saved to {output_file}")
