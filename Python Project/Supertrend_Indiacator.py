import yfinance as yf
import pandas as pd
import pandas_ta as ta
from datetime import datetime
import logging
import psycopg2

# Clear the log file before starting
with open('supertrend_analysis.log', 'w'):
    pass

# Configure logging
logging.basicConfig(filename='supertrend_analysis.log', level=logging.INFO, 
                    format='%(asctime)s - %(levelname)s - %(message)s')

def analyze_supertrend(ticker):
    """Analyze a single stock using Supertrend indicator"""
    try:
        # Download stock data
        stock = yf.Ticker(ticker + ".NS")
        df = stock.history(interval='1wk', period='5y')  # Get maximum available history
        
        # Calculate Supertrend
        supertrend = df.ta.supertrend(length=9, multiplier=2)
        
        # Merge Supertrend results with the original dataframe
        df = pd.concat([df, supertrend], axis=1)
        
        # Initialize trend counters
        uptrend_count = 0
        downtrend_count = 0
        
        # Log last 4 weeks of data
        logging.info(f"\n=== Last 4 Weeks Data for {ticker} ===")
        logging.info("=" * 80)
        recent_data = df.tail(4)
        for idx, row in recent_data.iterrows():
            date = idx.strftime('%Y-%m-%d')
            price = row['Close']
            supertrend_value = row['SUPERT_9_2.0']
            trend = "UPTREND" if row['SUPERTd_9_2.0'] == 1 else "DOWNTREND"
            distance = abs(price - supertrend_value)
            distance_percent = (distance / price) * 100
            
            if trend == "UPTREND":
                uptrend_count += 1
            else:
                downtrend_count += 1
            
            logging.info(f"Date: {date}")
            logging.info(f"Close Price: {price:.2f}")
            logging.info(f"Supertrend: {supertrend_value:.2f}")
            logging.info(f"Trend: {trend}")
            logging.info(f"Distance to Supertrend: {distance:.2f} ({distance_percent:.2f}%)")
            logging.info("-" * 50)
        
        logging.info(f"UPTREND count: {uptrend_count}")
        logging.info(f"DOWNTREND count: {downtrend_count}")
        
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
            'Distance Percentage': round(distance_percent, 2),
            'Uptrend Count': uptrend_count,
            'Downtrend Count': downtrend_count
        }
        
    except Exception as e:
        logging.error(f"Error analyzing {ticker}: {str(e)}")
        return {
            'Ticker': ticker,
            'Date': None,
            'Current Price': None,
            'Supertrend Value': None,
            'Decision': 'ERROR',
            'Reason': f'Error: {str(e)}',
            'Distance to Supertrend': None,
            'Distance Percentage': None,
            'Uptrend Count': None,
            'Downtrend Count': None
        }

# Read tickers from CSV file
try:
    tickers_df = pd.read_csv('tickers.csv')
    tickers = tickers_df['Ticker'].tolist()
except Exception as e:
    logging.error(f"Error reading CSV file: {str(e)}")
    logging.error("Please ensure you have a 'tickers.csv' file with a 'Ticker' column")
    exit()

# Analyze all stocks
results = []
for ticker in tickers:
    logging.info(f"\nAnalyzing {ticker}...")
    result = analyze_supertrend(ticker)
    results.append(result)

# Create results DataFrame
results_df = pd.DataFrame(results)

# Add timestamp to filename
timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
output_filename = f'supertrend_analysis_{timestamp}.xlsx'

# Save to Excel
results_df.to_excel(output_filename, sheet_name='Supertrend Analysis', index=False)

logging.info(f"\nAnalysis complete! Results saved to '{output_filename}'")

# Database connection parameters
db_params = {
    "dbname": "your_db_name",
    "user": "your_db_user",
    "password": "your_db_password",
    "host": "localhost",
    "port": "5432"
}

# Flag to control data insertion
insert_flag = True

if insert_flag:
    conn = None
    cursor = None
    try:
        # Connect to the PostgreSQL database
        conn = psycopg2.connect(**db_params)
        cursor = conn.cursor()
        
        # Check if table exists, if not create it
        create_table_query = """
        CREATE TABLE IF NOT EXISTS supertrend_analysis (
            Ticker VARCHAR(10),
            Date DATE,
            Current_Price FLOAT,
            Supertrend_Value FLOAT,
            Decision VARCHAR(10),
            Reason TEXT,
            Distance_to_Supertrend FLOAT,
            Distance_Percentage FLOAT,
            Uptrend_Count INT,
            Downtrend_Count INT
        )
        """
        cursor.execute(create_table_query)
        conn.commit()
        
        # Insert data into the table
        insert_query = """
        INSERT INTO supertrend_analysis (Ticker, Date, Current_Price, Supertrend_Value, Decision, Reason, Distance_to_Supertrend, Distance_Percentage, Uptrend_Count, Downtrend_Count)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """
        for index, row in results_df.iterrows():
            cursor.execute(insert_query, (
                row['Ticker'], row['Date'], row['Current Price'], row['Supertrend Value'], 
                row['Decision'], row['Reason'], row['Distance to Supertrend'], row['Distance Percentage'],
                row['Uptrend Count'], row['Downtrend Count']
            ))
        conn.commit()
        
        logging.info("Data successfully inserted into the PostgreSQL database.")
    except Exception as e:
        logging.error(f"Error inserting data into the database: {str(e)}")
    finally:
        if cursor is not None:
            cursor.close()
        if conn is not None:
            conn.close()