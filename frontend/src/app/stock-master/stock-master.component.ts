import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { finalize } from 'rxjs';
import { StockMaster, StockMasterPayload, StockMasterService } from './stock-master.service';

@Component({ selector: 'app-stock-master', imports: [FormsModule, Paginator], templateUrl: './stock-master.component.html', styleUrl: './stock-master.component.scss' })
export class StockMasterComponent implements OnInit {
  private readonly service = inject(StockMasterService);
  readonly stocks = signal<StockMaster[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly importing = signal(false);
  readonly formOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly message = signal<{ kind: 'success' | 'error'; text: string } | null>(null);
  readonly first = signal(0);
  readonly rows = signal(10);
  readonly pagedStocks = computed(() => this.stocks().slice(this.first(), this.first() + this.rows()));
  readonly rowsPerPageOptions = [10, 20, 50];
  search = '';
  form: StockMasterPayload = this.emptyForm();

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.service.list(this.search).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: stocks => { this.stocks.set(stocks); this.first.set(0); },
      error: () => this.showError('Stock records could not be loaded. Make sure the Spring Boot application is running.')
    });
  }
  openCreate(): void { this.editingId.set(null); this.form = this.emptyForm(); this.formOpen.set(true); this.message.set(null); }
  openEdit(stock: StockMaster): void { this.editingId.set(stock.id); this.form = { symbol: stock.symbol, nameOfCompany: stock.nameOfCompany, series: stock.series, isinNumber: stock.isinNumber, industry: stock.industry || '' }; this.formOpen.set(true); this.message.set(null); }
  closeForm(): void { this.formOpen.set(false); }
  save(): void {
    const id = this.editingId();
    const operation = id === null ? this.service.create(this.form) : this.service.update(id, this.form);
    this.saving.set(true);
    operation.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: stock => { this.formOpen.set(false); this.message.set({ kind: 'success', text: `${stock.symbol} was ${id === null ? 'added' : 'updated'} successfully.` }); this.load(); },
      error: error => this.showError(error?.error?.message || 'The stock record could not be saved.')
    });
  }
  remove(stock: StockMaster): void {
    if (!window.confirm(`Delete ${stock.symbol} from the stock master?`)) return;
    this.service.delete(stock.id).subscribe({
      next: () => { this.message.set({ kind: 'success', text: `${stock.symbol} was deleted successfully.` }); this.load(); },
      error: error => this.showError(error?.error?.message || 'The stock record could not be deleted.')
    });
  }
  pageChanged(event: PaginatorState): void {
    this.first.set(event.first ?? 0);
    this.rows.set(event.rows ?? 10);
  }
  selectCsv(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!file.name.toLowerCase().endsWith('.csv')) { this.showError('Please select a CSV file.'); return; }
    const reader = new FileReader();
    reader.onload = () => {
      const firstLine = String(reader.result ?? '').replace(/^\uFEFF/, '').split(/\r?\n/, 1)[0];
      const expected = 'Company Name,Industry,Symbol,Series,ISIN Code';
      if (firstLine.split(',').map(value => value.trim()).join(',') !== expected) {
        this.showError(`Invalid columns. Expected exactly: ${expected}`); return;
      }
      if (!window.confirm('Replace the complete Stock Master list with this CSV? Existing records not present in the file will be removed.')) return;
      this.importing.set(true); this.message.set(null);
      this.service.importCsv(file).pipe(finalize(() => this.importing.set(false))).subscribe({
        next: result => { this.message.set({ kind: 'success', text: `CSV uploaded successfully: ${result.totalRecords} total, ${result.created} added, ${result.updated} updated and ${result.removed} removed.` }); this.search = ''; this.load(); },
        error: error => this.showError(error?.error?.message || 'The CSV could not be imported. Existing data was not changed.')
      });
    };
    reader.onerror = () => this.showError('The selected CSV file could not be read.');
    reader.readAsText(file);
  }
  trackStock(_: number, stock: StockMaster): number { return stock.id; }
  private showError(text: string): void { this.message.set({ kind: 'error', text }); }
  private emptyForm(): StockMasterPayload { return { symbol: '', nameOfCompany: '', series: 'EQ', isinNumber: '', industry: '' }; }
}
