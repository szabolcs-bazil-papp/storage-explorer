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
import {EntryLoadResultType} from '../../../api/se';
import {Fieldset} from 'primeng/fieldset';

@Component({
  selector: 'sequence-inspector',
  imports: [
    Fieldset
  ],
  template: `
    <p-fieldset>
      <ng-template #header>
        <h2>{{ entry()?.uri ?? 'Unknown URI' }}</h2>
      </ng-template>
      <div class="sequence-card">
        <h2>Current value:</h2>
        <h1>{{ currVal() }}</h1>
      </div>
    </p-fieldset>`,
  styles: `
    .sequence-card {
      display: flex;
      align-items: center;
      gap: 1rem;
    }`
})
export class SequenceInspector extends AbstractInspector {

  currVal = computed<string>(() => {
    const _loadResult = this.loadResult();
    if (EntryLoadResultType.FAILED === _loadResult.type) {
      return 'Value currently not available.';
    }

    return this.entry()?.seqVal?.toString() ?? 'Unknown Value';
  });

}
