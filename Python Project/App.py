import pandas as pd
import yfinance as yf
import psycopg2
from datetime import datetime


# Function to connect to PostgreSQL database
def connect_db():
    try:
        conn = psycopg2.connect(
            dbname="factor_investing",
            user="tushardesarda",
            password="",
            host="localhost",
            port="5432"
        )
        return conn
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return None


# Function to create table if it doesn't exist
def create_table(conn):
    with conn.cursor() as cursor:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS stock_price_data (
                id SERIAL PRIMARY KEY,
                ticker VARCHAR(10),
                date DATE,
                closing_price DECIMAL(10, 2) 
            );
        """)
        conn.commit()


# Function to insert data into the database
def insert_data(conn, ticker, date, closing_price):
    with conn.cursor() as cursor:
        cursor.execute("""
            INSERT INTO stock_price_data (ticker, date, closing_price)
            VALUES (%s, %s, %s);
        """, (ticker, date, closing_price))
        conn.commit()


# Main function to fetch stock data and store it in PostgreSQL
def fetch_and_store_stock_data(csv_file):
    # Read ticker symbols from CSV file
    tickers = pd.read_csv(csv_file)['Ticker'].tolist()

    # Connect to the database
    conn = connect_db()
    if conn is None:
        return

    # Create table if it doesn't exist
    create_table(conn)

    for ticker in tickers:
        try:
            # Fetch historical data for the last 30 days
            stock_price_data = yf.Ticker(ticker + '.NS').history(period='8y')  # Append '.NS' for NSE stocks

            # Iterate over the fetched data and insert into database
            for index, row in stock_price_data.iterrows():
                insert_data(conn, ticker, index.date(), row['Close'])

            print(f"Data for {ticker} stored successfully.")

        except Exception as e:
            print(f"Error fetching data for {ticker}: {e}")

    # Close the database connection
    conn.close()


# Execute the program with your CSV file path
fetch_and_store_stock_data('nse_tickers.csv')