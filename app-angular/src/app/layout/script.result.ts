/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

import {Component, computed, inject} from '@angular/core';
import {ArcScriptColumnDescriptor} from '../../api/se';
import {TableModule} from 'primeng/table';
import {ScrollPanel} from 'primeng/scrollpanel';
import {Fieldset} from 'primeng/fieldset';
import {AppService} from '../app.service';

@Component({
  selector: 'script-result',
  imports: [
    TableModule,
    ScrollPanel,
    Fieldset
  ],
  template: `
    <p-scroll-panel [style]="{height: '100%', width: '100%', 'padding-left': '1rem', 'padding-right': '1rem'}">
      <p-fieldset legend="Query Results">
        <p-table [value]="service.scriptResult()?.resultSet || []"
                 [columns]="cols()"
                 stripedRows
                 [scrollable]="true"
                 scrollHeight="flex"
                 size="small"
                 selectionMode="single"
                 paginator
                 [rowsPerPageOptions]="[10, 20, 50, 100, 200]"
                 [rows]="10"

                 sortMode="multiple">
          <ng-template #header let-columns>
            <tr class="res-header">
              @for (col of columns; track col.column) {
                <th pSortableColumn="{{col.alias ?? col.column}}">
                  <div class="">
                    {{ col.alias ?? col.column }}
                    <p-sortIcon [field]="col.alias ?? col.column"/>
                  </div>
                </th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-rowData let-columns="columns">
            <tr (dblclick)="onRowInteraction($event, rowData)">
              @for (col of columns; track col.column) {
                <td>{{ rowData[col.alias ?? col.column] }}</td>
              }
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr>
              <td [colSpan]="cols().length">No entries found.</td>
            </tr>
          </ng-template>

        </p-table>
      </p-fieldset>
    </p-scroll-panel>
  `,
  styles: `
    .res-header {
      position: sticky;
      top: 0;
      z-index: 1000;
    }`
})
export class ScriptResult {

  service = inject(AppService);

  cols = computed<Array<ArcScriptColumnDescriptor>>(() => {
    return this.service.scriptResult()?.columns ?? [ { column: 'uri', alias: 'URI' }];
  });

  onRowInteraction(event: MouseEvent, rowData: any) {
    event.stopPropagation();

    const uri = rowData['uri'];
    if (uri) {
      this.service.performAcquire(uri)
    } else {
      // TODO: Show toast
    }
  }

}
