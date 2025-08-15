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

import {Component, computed} from '@angular/core';
import {AbstractInspector} from './abstract.inspector';
import {TableModule} from 'primeng/table';
import {EntryLoadResultType, Reference} from '../../../api/se';

@Component({
  selector: 'list-inspector',
  imports: [
    TableModule
  ],
  template: `
    <div class="list-inspector-wrapper">
      <h2>{{ entry()?.uri ?? 'Unknown URI' }}</h2>
      <p-table [value]="elements()">
        <ng-template #header>
          <tr>
            <th>Index</th>
            <th>URI</th>
          </tr>
        </ng-template>
        <ng-template #body let-reference>
          <tr>
            <td>{{ reference.pos }}</td>
            <td (dblclick)="onRowDblClick($event, reference)">{{ reference.uri }}</td>
          </tr>
        </ng-template>
      </p-table>
    </div>`
})
export class ListInspector extends AbstractInspector {


  elements = computed<Array<Reference>>(() => {
    const _loadResult = this.loadResult();
    if (EntryLoadResultType.FAILED ===  _loadResult.type) {
      return [];
    }

    const res = (this.entry()?.references ?? []).sort((a, b) => (a.pos ?? 0) - (b.pos ?? 0));
    console.log(res);
    return res;
  })

  onRowDblClick(event: MouseEvent, reference: Reference) {
    event.stopPropagation();
    this.service.performAcquire(reference.uri);
  }
}

