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
import {
  AfterViewInit,
  Component,
  computed,
  effect,
  ElementRef,
  signal,
  viewChild
} from '@angular/core';
import {EntryLoadResult, EntryLoadResultType} from '../../../api/se';
import * as Prism from 'prismjs';
import 'prismjs/components/index'
import 'prismjs/components/prism-json';

@Component({
  selector: 'object-inspector',
  template: `
    <div class="object-inspector-container">
      @if (entry()) {
        <p>{{ entry()!.uri }}</p>
      }
      @if (loadResult().type === EntryLoadResultType.FAILED) {
        <i>Entry is currently not available...</i>
      }
      <pre><code class="language-json" #oam> {{ oamStr() }}</code></pre>
    </div>`
})
export class ObjectInspector extends AbstractInspector implements AfterViewInit {


  loadResult = signal<EntryLoadResult>({type: EntryLoadResultType.FAILED, versions: []});

  v = signal(0);

  oamStr = computed(() => {
    const versions = this.loadResult()?.versions;
    const version = this.v();
    if (versions && version < versions.length) {
      console.log('printing oam')
      return JSON.stringify(versions[version].objectAsMap, null, 2);
    } else {
      console.log('no oam, ', versions,  version);
      return '';
    }
  })

  constructor() {
    super();
    effect(() => {
      const entry = this.entry();
      if (!entry) {
        return;
      }

      this.service.load(entry).then(res => {
        this.loadResult.set(res);
        this.v.set(res.versions.length - 1);
      });
    });
    effect(() => {
      const vStr = this.oamStr();
      console.log('OAM changed: ', vStr);
      this.oam().nativeElement.textContent = vStr;
      Prism.highlightElement(this.oam().nativeElement);
    });
  }

  protected readonly EntryLoadResultType = EntryLoadResultType;

  oam = viewChild.required('oam', {read: ElementRef});

  ngAfterViewInit(): void {
    Prism.highlightElement(this.oam().nativeElement);
  }
}
