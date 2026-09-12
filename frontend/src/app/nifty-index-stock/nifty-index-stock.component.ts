import {Component,OnInit,computed,inject,signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {NgFor} from '@angular/common';
import {Paginator,PaginatorState} from '@openng/optimus-ui/paginator';
import {finalize} from 'rxjs';
import {NiftyIndexName,NiftyIndexStockDetail,NiftyIndexStockService} from './nifty-index-stock.service';
const INDEX_OPTIONS:{value:NiftyIndexName;label:string}[]=[
  {value:'NIFTY50',label:'nifty 50'},
  {value:'NIFTY500',label:'nifty 500'},
  {value:'NIFTY750',label:'nifty 750'},
  {value:'NIFTY_MIDCAP150',label:'nifty midcap150'},
  {value:'NIFTY_NEXT50',label:'nifty next50'},
  {value:'NIFTY_SMALLCAP250',label:'nifty smallcap250'},
  {value:'NIFTY200',label:'nifty 200'},
];
@Component({selector:'app-nifty-index-stock',imports:[FormsModule,NgFor,Paginator],templateUrl:'./nifty-index-stock.component.html',styleUrl:'./nifty-index-stock.component.scss'})
export class NiftyIndexStockComponent implements OnInit{
 private readonly service=inject(NiftyIndexStockService);
 readonly indexOptions=INDEX_OPTIONS;
 selectedIndex:NiftyIndexName='NIFTY50';
 readonly stocks=signal<NiftyIndexStockDetail[]>([]);
 readonly loading=signal(false);
 readonly importing=signal(false);
 readonly saving=signal(false);
 readonly message=signal<{kind:'success'|'error';text:string}|null>(null);
 readonly first=signal(0);readonly rows=signal(10);readonly rowsPerPageOptions=[10,20,50];
 readonly pagedStocks=computed(()=>this.stocks().slice(this.first(),this.first()+this.rows()));
 bulkSymbols='';
 ngOnInit(){this.load();}
 load(){
  this.loading.set(true);this.message.set(null);
  this.service.listForIndex(this.selectedIndex).pipe(finalize(()=>this.loading.set(false))).subscribe({
   next:data=>{this.stocks.set(data);this.first.set(0);},
   error:()=>this.error('Stock list could not be loaded. Make sure Spring Boot is running.'),
  });
 }
 onIndexChange(){this.bulkSymbols='';this.load();}
 applyBulkSymbols(){
  const symbols=[...new Set(this.bulkSymbols.split(/\r?\n/).map(line=>line.trim().toUpperCase()).filter(Boolean))];
  if(!symbols.length){this.error('Enter at least one stock symbol, one per line.');return;}
  if(!window.confirm(`Replace the stock list for ${this.selectedIndexLabel()} with these ${symbols.length} symbols?`))return;
  this.saving.set(true);
  this.service.replaceWithSymbols(this.selectedIndex,symbols).pipe(finalize(()=>this.saving.set(false))).subscribe({
   next:r=>{this.bulkSymbols='';this.message.set({kind:'success',text:r.message});this.load();},
   error:e=>this.error(e?.error?.message||'The stock list could not be saved.'),
  });
 }
 selectCsv(event:Event){
  const input=event.target as HTMLInputElement;const file=input.files?.[0];input.value='';if(!file)return;
  if(!file.name.toLowerCase().endsWith('.csv')){this.error('Please select a CSV file.');return;}
  if(!window.confirm(`Replace the stock list for ${this.selectedIndexLabel()} with this CSV?`))return;
  this.importing.set(true);
  this.service.importCsv(this.selectedIndex,file).pipe(finalize(()=>this.importing.set(false))).subscribe({
   next:r=>{this.message.set({kind:'success',text:r.message});this.load();},
   error:e=>this.error(e?.error?.message||'CSV import failed. Existing data was not changed.'),
  });
 }
 pageChanged(e:PaginatorState){this.first.set(e.first??0);this.rows.set(e.rows??10);}
 selectedIndexLabel(){return this.indexOptions.find(option=>option.value===this.selectedIndex)?.label??this.selectedIndex;}
 downloadTemplate(){
  const rows=['Symbol','RELIANCE','TCS','HDFCBANK'];
  const csv=rows.join('\n');
  const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download=`${this.selectedIndexLabel()}-template.csv`;link.click();URL.revokeObjectURL(url);
 }
 private error(text:string){this.message.set({kind:'error',text});}
}
