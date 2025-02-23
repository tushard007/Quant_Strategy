import yfinance as yf
import pandas as pd
import pandas_ta as ta
from datetime import datetime

def analyze_supertrend(ticker):
    """Analyze a single stock using Supertrend indicator"""
    try:
        # Download stock data
        stock = yf.Ticker(ticker)
        df = stock.history(interval='1wk', period='max')  # Get maximum available history
        
        # Calculate Supertrend
        supertrend = df.ta.supertrend(length=9, multiplier=2)
        
        # Merge Supertrend results with the original dataframe
        df = pd.concat([df, supertrend], axis=1)
        
        # Print last 4 weeks of data
        print(f"\n=== Last 4 Weeks Data for {ticker} ===")
        print("=" * 80)
        recent_data = df.tail(4)
        for idx, row in recent_data.iterrows():
            date = idx.strftime('%Y-%m-%d')
            price = row['Close']
            supertrend_value = row['SUPERT_9_2.0']
            trend = "UPTREND" if row['SUPERTd_9_2.0'] == 1 else "DOWNTREND"
            distance = abs(price - supertrend_value)
            distance_percent = (distance / price) * 100
            
            print(f"Date: {date}")
            print(f"Close Price: ${price:.2f}")
            print(f"Supertrend: ${supertrend_value:.2f}")
            print(f"Trend: {trend}")
            print(f"Distance to Supertrend: ${distance:.2f} ({distance_percent:.2f}%)")
            print("-" * 50)
        
        # Get last week's data for Excel export
        last_week = df.iloc[-1]
        current_price = last_week['Close']
        supertrend_value = last_week['SUPERT_9_2.0']
        trend_direction = last_week['SUPERTd_9_2.0']
        
        # Make trading decision
        if trend_direction == 1:
            decision = "BUY"
            reason = "Stock is in UPTREND"
        elif trend_direction == -1:
            decision = "SELL"
            reason = "Stock is in DOWNTREND"
        else:
            decision = "NO SIGNAL"
            reason = "No clear trend"
            
        # Calculate distance
        distance = abs(current_price - supertrend_value)
        distance_percent = (distance / current_price) * 100
        
        return {
            'Ticker': ticker,
            'Date': last_week.name.strftime('%Y-%m-%d'),
            'Current Price': round(current_price, 2),
            'Supertrend Value': round(supertrend_value, 2),
            'Decision': decision,
            'Reason': reason,
            'Distance to Supertrend': round(distance, 2),
            'Distance Percentage': round(distance_percent, 2)
        }
        
    except Exception as e:
        print(f"Error analyzing {ticker}: {str(e)}")
        return {
            'Ticker': ticker,
            'Date': None,
            'Current Price': None,
            'Supertrend Value': None,
            'Decision': 'ERROR',
            'Reason': f'Error: {str(e)}',
            'Distance to Supertrend': None,
            'Distance Percentage': None
        }

# Read tickers from CSV file
try:
    tickers_df = pd.read_csv('tickers.csv')
    tickers = tickers_df['Ticker'].tolist()
except Exception as e:
    print(f"Error reading CSV file: {str(e)}")
    print("Please ensure you have a 'tickers.csv' file with a 'Ticker' column")
    exit()

# Analyze all stocks
results = []
for ticker in tickers:
    print(f"\nAnalyzing {ticker}...")
    result = analyze_supertrend(ticker)
    results.append(result)

# Create results DataFrame
results_df = pd.DataFrame(results)

# Add timestamp to filename
timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
output_filename = f'supertrend_analysis_{timestamp}.xlsx'

# Save to Excel
results_df.to_excel(output_filename, sheet_name='Supertrend Analysis', index=False)

print(f"\nAnalysis complete! Results saved to '{output_filename}'")