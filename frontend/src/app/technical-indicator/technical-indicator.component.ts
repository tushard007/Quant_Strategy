import {DecimalPipe} from '@angular/common';
import {Component,computed,inject,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Paginator,PaginatorState} from '@openng/optimus-ui/paginator';
import {finalize,Observable} from 'rxjs';
import {PriceFrequency,TechnicalAssetType,TechnicalIndicatorService} from './technical-indicator.service';

type IndicatorType='EMA'|'SUPER_TREND';
interface IndicatorRow{symbol:string;closingPrice:number;indicatorValue:number;percentageDifference:number;upperBand?:number;lowerBand?:number;trend?:string;}

@Component({selector:'app-technical-indicator',imports:[FormsModule,DecimalPipe,Paginator],templateUrl:'./technical-indicator.component.html',styleUrl:'./technical-indicator.component.scss'})
export class TechnicalIndicatorComponent{
 private readonly service=inject(TechnicalIndicatorService);readonly loading=signal(false);readonly rowsData=signal<IndicatorRow[]>([]);readonly error=signal<string|null>(null);readonly message=signal<string|null>(null);readonly first=signal(0);readonly rows=signal(20);readonly rowsPerPageOptions=[20,50,100];indicatorType:IndicatorType='EMA';assetDataType:TechnicalAssetType='STOCK';days=20;multiplier=3;priceFrequency:PriceFrequency='DAILY';
 readonly visibleRows=computed(()=>this.rowsData().slice(this.first(),this.first()+this.rows()));
 calculate(){if(!Number.isInteger(this.days)||this.days<1){this.error.set('Days must be a whole number greater than zero.');return;}if(this.indicatorType==='SUPER_TREND'&&(!Number.isFinite(this.multiplier)||this.multiplier<=0)){this.error.set('Multiplier must be greater than zero.');return;}this.loading.set(true);this.error.set(null);this.message.set(null);this.rowsData.set([]);this.first.set(0);const request=(this.indicatorType==='EMA'?this.service.ema(this.days,this.assetDataType):this.service.superTrend(this.days,this.assetDataType,this.multiplier,this.priceFrequency)) as Observable<Record<string,unknown>>;request.pipe(finalize(()=>this.loading.set(false))).subscribe({next:response=>{const rows=this.indicatorType==='EMA'?this.mapEma(response as Record<string,number[]>):this.mapSuperTrend(response as Record<string,{closingPrice:number;superTrendValue:number;percentageDifference:number;upperBand:number;lowerBand:number;trend:string}>);this.rowsData.set(rows.sort((a,b)=>b.percentageDifference-a.percentageDifference));this.message.set(`${rows.length} ${this.indicatorType==='EMA'?'EMA':'SuperTrend'} results calculated successfully.`);},error:(error:unknown)=>{const response=error as {error?:{message?:string}|string};this.error.set(typeof response.error==='string'?response.error:response.error?.message||'Technical indicator calculation failed. Make sure price data is available and Spring Boot is running.');}});}
 indicatorChanged(){this.rowsData.set([]);this.message.set(null);this.error.set(null);this.first.set(0);}pageChanged(event:PaginatorState){this.first.set(event.first??0);this.rows.set(event.rows??20);}
 private mapEma(response:Record<string,number[]>){return Object.entries(response).map(([symbol,values])=>({symbol,closingPrice:Number(values?.[0]??0),indicatorValue:Number(values?.[1]??0),percentageDifference:Number(values?.[2]??0)}));}
 private mapSuperTrend(response:Record<string,{closingPrice:number;superTrendValue:number;percentageDifference:number;upperBand:number;lowerBand:number;trend:string}>){return Object.entries(response).map(([symbol,value])=>({symbol,closingPrice:Number(value.closingPrice??0),indicatorValue:Number(value.superTrendValue??0),percentageDifference:Number(value.percentageDifference??0),upperBand:Number(value.upperBand??0),lowerBand:Number(value.lowerBand??0),trend:value.trend}));}
}
