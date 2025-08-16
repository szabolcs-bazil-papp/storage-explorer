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

import {Component, input, output} from '@angular/core';
import {TableModule} from 'primeng/table';
import {Reference} from '../../../../api/se';

export type FirstColValueExtractor = (ref: Reference) => string;

@Component({
  selector: 'uri-table',
  imports: [
    TableModule
  ],
  template: `
    <p-table [value]="elements()">
      <ng-template #header>
        <tr>
          <th>{{ firstColHeader() }}</th>
          <th>URI</th>
        </tr>
      </ng-template>
      <ng-template #body let-reference>
        <tr>
          <td>{{ firstColValue()(reference) }}</td>
          <td (dblclick)="onRowDblClick($event, reference)">{{ reference.uri }}</td>
        </tr>
      </ng-template>
    </p-table>`
})
export class UriTable {

  firstColHeader = input.required<string>();
  firstColValue = input.required<FirstColValueExtractor>();

  elements = input.required<Array<Reference>>();
  onDblClick = output<{ e: MouseEvent; ref: Reference; }>();

  onRowDblClick(event: MouseEvent, reference: Reference) {
    event.stopPropagation();
    this.onDblClick.emit({e: event, ref: reference});
  }

}
