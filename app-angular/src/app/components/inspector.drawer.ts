import {Component, computed, inject, model, output} from '@angular/core';
import {AppService} from '../app.service';
import {FloatLabel} from 'primeng/floatlabel';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {Listbox} from 'primeng/listbox';
import {StorageEntryDto} from '../../api/se';
import {InputIcon} from 'primeng/inputicon';
import {IconField} from 'primeng/iconfield';

@Component({
  selector: 'inspector-drawer',
  imports: [
    FloatLabel,
    InputText,
    FormsModule,
    Listbox,
    InputIcon,
    IconField,
  ],
  template: `
    <div class="drawer-wrapper">
      <p-icon-field>
        <p-inputicon class="pi pi-search"></p-inputicon>
        <p-floatlabel class="drawer-query">
          <input id="inspector_query" pInputText [(ngModel)]="query" class="drawer-query-input">
          <label for="inspector_query">Search for Inspector</label>
        </p-floatlabel>
      </p-icon-field>

      <p-listbox class="drawer-list" [options]="items()" optionLabel="URI" (onDblClick)="onRowDblClick($event)">
        <ng-template #item let-entry>
            <span>{{ entry.uri }}</span>
        </ng-template>
      </p-listbox>
    </div>`,
  styles: `
  .drawer-wrapper {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }
  .drawer-query {

  }

  ::ng-deep .drawer-list div.p-listbox-list-container {
    max-height: 85vh !important;
  }

  .drawer-query-input {
    min-width: 50em;
  }
  .drawer-list {}`

})
export class InspectorDrawer {

  query = model<string>('');

  service = inject(AppService);

  closeRequest = output<void>();

  items = computed(() => {
    const _query = this.query().toLowerCase();
    return this.service.openInspectors().filter(it => it.uri.toLowerCase().includes(_query) || it.name.toLowerCase().includes(_query));
  })

  onRowDblClick(event: any) {
    const entry = event.option as StorageEntryDto;
    this.service.performAcquire(entry.uri).then(() => this.closeRequest.emit());
  }

}
