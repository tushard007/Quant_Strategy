import {HttpClient,HttpParams} from '@angular/common/http';
import {inject,Injectable} from '@angular/core';

export type TechnicalAssetType='STOCK'|'ETF'|'INDEX';
export type PriceFrequency='DAILY'|'WEEKLY';
export interface SuperTrendResponse{closingPrice:number;superTrendValue:number;percentageDifference:number;upperBand:number;lowerBand:number;trend:string;}

@Injectable({providedIn:'root'})
export class TechnicalIndicatorService{
 private readonly http=inject(HttpClient);
 ema(days:number,assetDataType:TechnicalAssetType){const params=new HttpParams().set('assetDataType',assetDataType);return this.http.get<Record<string,number[]>>(`/api/technical-indicator/EMAIndicator/${days}`,{params});}
 superTrend(days:number,assetDataType:TechnicalAssetType,multiplier:number,priceFrequencey:PriceFrequency){const params=new HttpParams().set('assetDataType',assetDataType).set('multiplier',multiplier).set('priceFrequencey',priceFrequencey);return this.http.get<Record<string,SuperTrendResponse>>(`/api/technical-indicator/SuperTrendIndicator/${days}`,{params});}
}
