import {HttpClient} from '@angular/common/http';
import {inject,Injectable} from '@angular/core';
import {HttpParams} from '@angular/common/http';
export type AssetDataType='STOCK'|'ETF'|'INDEX';
export interface MomentumAsset{stockName:string;oneYearReturn:number;sixMonthReturn:number;threeMonthReturn:number;qualifiesForMomentum:boolean;strategyRunDate:string;rank12Months?:number;rank6Months?:number;rank3Months?:number;totalRankScore?:number;}
export interface MomentumResult{allStocks:MomentumAsset[];qualifiedStocks:MomentumAsset[];topStockNames:string[];totalAnalyzed:number;qualifiedCount:number;valid:boolean;message:string;}
export interface MomentumExecution{assetDataType:AssetDataType;strategyRunDate:string;resultCount:number;lastUpdatedAt:string|null;}
export interface SavedMomentumAsset{stockName:string;oneYearReturn:number;sixMonthReturn:number;threeMonthReturn:number;strategyRunDate:string;rank12Months:number;rank6Months:number;rank3Months:number;totalRankScore:number;}
@Injectable({providedIn:'root'}) export class MomentumAnalysisService{private readonly http=inject(HttpClient);run(type:AssetDataType){return this.http.post<MomentumResult>(`/api/momentum/calculate-and-rank/${type}`,null);}executions(type?:AssetDataType){const params=type?new HttpParams().set('assetDataType',type):undefined;return this.http.get<MomentumExecution[]>('/api/momentum/executions',{params});}savedResults(type:AssetDataType,date:string){return this.http.get<SavedMomentumAsset[]>(`/api/momentum/executions/${type}/${date}`);}}
