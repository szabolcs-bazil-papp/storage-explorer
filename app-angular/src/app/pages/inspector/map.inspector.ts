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

import {AbstractInspector} from './abstract.inspector';
import {Component, computed} from '@angular/core';
import {EntryLoadResultType, Reference} from '../../../api/se';
import {UriTable} from './components/uri.table';

export function extractPropName(ref: Reference) {
  return ref.propName ?? '<<Unknown Key>>';
}

@Component({
  selector: 'map-inspector',
  imports: [
    UriTable
  ],
  template: `
    <div class="map-inspector-wrapper">
      <h2>{{ entry()?.uri ?? 'Unknown URI' }}</h2>
      <uri-table firstColHeader="Key"
                 [firstColValue]="extractPropName"
                 (onDblClick)="onRowDblClick($event.e, $event.ref)"
                 [elements]="elements()">
      </uri-table>
    </div>`
})
export class MapInspector extends AbstractInspector {

  elements = computed<Array<Reference>>(() => {
    const _loadResult = this.loadResult();
    if (EntryLoadResultType.FAILED === _loadResult.type) {
      return [];
    }

    const res = (this.entry()?.references ?? []).sort((a, b) => a.propName?.localeCompare(b.propName) ?? 0);
    console.log(res);
    return res;
  });

  onRowDblClick(event: MouseEvent, reference: Reference) {
    event.stopPropagation();
    this.service.performAcquire(reference.uri);
  }

  protected readonly extractPropName = extractPropName;

}
