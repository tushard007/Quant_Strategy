import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime

def atr(df, period=14):
    """Calculate Average True Range (ATR) using EMA."""
    high_low = df['High'] - df['Low']
    high_close = np.abs(df['High'] - df['Close'].shift())
    low_close = np.abs(df['Low'] - df['Close'].shift())

    tr = pd.DataFrame({'high_low': high_low, 'high_close': high_close, 'low_close': low_close})
    tr['TR'] = tr.max(axis=1)

    # Use EMA for ATR calculation (more accurate)
    atr = tr['TR'].ewm(span=period, adjust=False).mean()
    return atr

def supertrend(df, period=9, multiplier=2):
    """Calculate Supertrend Indicator"""
    df = df.copy()
    df['ATR'] = atr(df, period)

    # Calculate basic bands
    hl2 = (df['High'] + df['Low']) / 2
    df['UpperBand'] = hl2 + (multiplier * df['ATR'])
    df['LowerBand'] = hl2 - (multiplier * df['ATR'])

    # Supertrend calculation
    df['Supertrend'] = np.nan
    df['FinalUpperBand'] = df['UpperBand']
    df['FinalLowerBand'] = df['LowerBand']

    for i in range(1, len(df)):
        # Upper Band Adjustment
        if df['UpperBand'].iloc[i] < df['FinalUpperBand'].iloc[i-1] or df['Close'].iloc[i-1] > df['FinalUpperBand'].iloc[i-1]:
            df.at[df.index[i], 'FinalUpperBand'] = df['UpperBand'].iloc[i]
        else:
            df.at[df.index[i], 'FinalUpperBand'] = df['FinalUpperBand'].iloc[i-1]

        # Lower Band Adjustment
        if df['LowerBand'].iloc[i] > df['FinalLowerBand'].iloc[i-1] or df['Close'].iloc[i-1] < df['FinalLowerBand'].iloc[i-1]:
            df.at[df.index[i], 'FinalLowerBand'] = df['LowerBand'].iloc[i]
        else:
            df.at[df.index[i], 'FinalLowerBand'] = df['FinalLowerBand'].iloc[i-1]

        # Determine Supertrend
        if np.isnan(df['Supertrend'].iloc[i-1]):
            df.at[df.index[i], 'Supertrend'] = df['FinalLowerBand'].iloc[i] if df['Close'].iloc[i] > df['FinalLowerBand'].iloc[i] else df['FinalUpperBand'].iloc[i]
        elif df['Supertrend'].iloc[i-1] == df['FinalUpperBand'].iloc[i-1] and df['Close'].iloc[i] <= df['FinalUpperBand'].iloc[i]:
            df.at[df.index[i], 'Supertrend'] = df['FinalUpperBand'].iloc[i]
        elif df['Supertrend'].iloc[i-1] == df['FinalUpperBand'].iloc[i-1] and df['Close'].iloc[i] > df['FinalUpperBand'].iloc[i]:
            df.at[df.index[i], 'Supertrend'] = df['FinalLowerBand'].iloc[i]
        elif df['Supertrend'].iloc[i-1] == df['FinalLowerBand'].iloc[i-1] and df['Close'].iloc[i] >= df['FinalLowerBand'].iloc[i]:
            df.at[df.index[i], 'Supertrend'] = df['FinalLowerBand'].iloc[i]
        elif df['Supertrend'].iloc[i-1] == df['FinalLowerBand'].iloc[i-1] and df['Close'].iloc[i] < df['FinalLowerBand'].iloc[i]:
            df.at[df.index[i], 'Supertrend'] = df['FinalUpperBand'].iloc[i]

    return df[['Close', 'Supertrend', 'FinalUpperBand', 'FinalLowerBand']]

# Read symbols from CSV file
csv_file = "nse_tickers.csv"  # Replace with your CSV file path
try:
    symbols_df = pd.read_csv(csv_file)
    if 'Symbol' not in symbols_df.columns:
        raise ValueError("CSV file must contain a 'Symbol' column")
    symbols = symbols_df['Symbol'].tolist()
except FileNotFoundError:
    print(f"Error: {csv_file} not found")
    exit()
except ValueError as e:
    print(f"Error: {e}")
    exit()

# Initialize an empty DataFrame to store summary data
all_summary_df = pd.DataFrame()

# Process each symbol
for symbol in symbols:
    try:
        stock = yf.Ticker(f"{symbol}.NS")
        df = stock.history(period="3y", interval="1wk")

        if df.empty:
            print(f"No data found for {symbol}")
            continue

        st_result = supertrend(df, period=9, multiplier=2)

        # Display last 5 values
        # print(f"\nSupertrend (9,2) for {symbol} (Weekly)")
        # print(st_result.tail(5))

        # Identify trend
        last_close = st_result['Close'].iloc[-1]
        last_supertrend = st_result['Supertrend'].iloc[-1]
        trend = "Uptrend" if last_close > last_supertrend else "Downtrend"
        
        print(f"\nSupertrend (9,2) for {symbol} (Weekly)")
        print(f"\nCurrent Trend: {trend}")
        print(f"Last Weekly Close Price: {last_close:.2f}")
        print(f"Last Supertrend Value: {last_supertrend:.2f}")

        # Append data to a single DataFrame
        summary_data = {
            'Symbol': symbol,
            'Current Trend': trend,
            'Last Weekly Close Price': last_close,
            'Last Supertrend Value': last_supertrend
        }
        all_summary_df = pd.concat([all_summary_df, pd.DataFrame([summary_data])], ignore_index=True)

    except Exception as e:
        print(f"Error processing {symbol}: {str(e)}")
        continue

# Save the combined DataFrame to an Excel file once after processing all symbols
now = datetime.now()
timestamp = now.strftime("%Y%m%d_%H%M%S")
excel_filename = f"all_symbols_summary_{timestamp}.xlsx"
all_summary_df.to_excel(excel_filename, index=False)
print(f"Summary saved to {excel_filename}")