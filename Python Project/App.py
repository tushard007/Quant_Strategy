import pandas as pd
import yfinance as yf
import psycopg
from datetime import datetime
import sys

def create_stock_price_table(conn):
    """Create the stock_prices table if it doesn't exist"""
    with conn.cursor() as cur:
        cur.execute("""
            CREATE TABLE IF NOT EXISTS stock_prices (
                id SERIAL PRIMARY KEY,
                symbol VARCHAR(10),
                date DATE,
                open FLOAT,
                high FLOAT,
                low FLOAT,
                close FLOAT,
                volume BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE (symbol, date)
            )
        """)
    conn.commit()

def load_stock_data(csv_path, conn):
    """
    Load stock data from CSV file, download price data, and store in database
    
    Args:
        csv_path (str): Path to CSV file containing stock symbols
        conn: Database connection object
    """
    try:
        # Read symbols from CSV file
        df_symbols = pd.read_csv(csv_path)
        if 'symbol' not in df_symbols.columns:
            raise ValueError("CSV file must contain a 'symbol' column")
        
        symbols = df_symbols['symbol'].tolist()
        print(f"Found {len(symbols)} symbols in CSV file")
        
        # Create table if it doesn't exist
        create_stock_price_table(conn)
        
        # Process each symbol
        for symbol in symbols:
            try:
                print(f"Processing {symbol}...")
                
                # Download data from Yahoo Finance
                stock = yf.Ticker(f"{symbol}.NS")
                df = stock.history(period="2y")
                
                if df.empty:
                    print(f"No data found for {symbol}")
                    continue
                
                # Reset index to make date a column
                df = df.reset_index()
                
                # Insert data into database
                with conn.cursor() as cur:
                    for _, row in df.iterrows():
                        cur.execute("""
                            INSERT INTO stock_prices (symbol, date, open, high, low, close, volume)
                            VALUES (%s, %s, %s, %s, %s, %s, %s)
                            ON CONFLICT (symbol, date) 
                            DO UPDATE SET
                                open = EXCLUDED.open,
                                high = EXCLUDED.high,
                                low = EXCLUDED.low,
                                close = EXCLUDED.close,
                                volume = EXCLUDED.volume,
                                created_at = CURRENT_TIMESTAMP
                        """, (
                            symbol,
                            row['Date'].date(),
                            float(row['Open']),
                            float(row['High']),
                            float(row['Low']),
                            float(row['Close']),
                            int(row['Volume'])
                        ))
                
                conn.commit()
                print(f"Successfully loaded data for {symbol}")
                
            except Exception as e:
                print(f"Error processing {symbol}: {str(e)}")
                conn.rollback()
                continue
                
    except Exception as e:
        print(f"Error: {str(e)}")
        sys.exit(1)

def main():
    # Database connection parameters
    db_params = {
        "dbname": "factor_investing",
        "user": "tushardesarda",
        "password": "",
        "host": "localhost",
        "port": "5432"
    }
    
    try:
        # Connect to database
        with psycopg.connect(**db_params) as conn:
            # Replace with your CSV file path
            csv_path = "nse_tickers.csv"
            load_stock_data(csv_path, conn)
            
    except Exception as e:
        print(f"Database connection error: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main()