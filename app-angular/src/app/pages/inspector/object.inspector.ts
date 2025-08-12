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
import {Component, computed, effect, signal} from '@angular/core';
import {EntryLoadResult, EntryLoadResultType} from '../../../api/se';
import {LanguageDescription} from '@codemirror/language';
import {CodeEditor} from '@acrodata/code-editor';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'object-inspector',
  imports: [
    CodeEditor,
    FormsModule
  ],
  template: `
    <div class="object-inspector-container">
      @if (entry()) {
        <p>{{ entry()!.uri }}</p>
      }
      @if (loadResult().type === EntryLoadResultType.FAILED) {
        <i>Entry is currently not available...</i>
      }
      <code-editor [ngModel]="oamStr()"
                   [languages]="_languages"
                   [language]="'JSON'"
                   [theme]="service.isDark() ? 'dark' : 'light'"
                   [disabled]="true"></code-editor>
    </div>`
})
export class ObjectInspector extends AbstractInspector {

  _languages: Array<LanguageDescription> = [LanguageDescription.of({
    name: "JSON",
    alias: ["json5"],
    extensions: ["json","map"],
    load() {
      return import("@codemirror/lang-json").then(m => m.json())
    }
  })];

  loadResult = signal<EntryLoadResult>({type: EntryLoadResultType.FAILED, versions: []});

  v = signal(0);

  oamStr = computed(() => {
    const versions = this.loadResult()?.versions;
    const version = this.v();
    if (versions && version < versions.length) {
      return JSON.stringify(versions[version].objectAsMap, null, 2);
    } else {
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
  }

  protected readonly EntryLoadResultType = EntryLoadResultType;

}
