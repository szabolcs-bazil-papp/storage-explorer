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
import {Component, computed, HostListener, viewChild} from '@angular/core';
import {EntryLoadResultType} from '../../../api/se';
import {LanguageDescription} from '@codemirror/language';
import {FormsModule} from '@angular/forms';
import {CodeEditor} from '@acrodata/code-editor';
import {Tab, TabList, Tabs} from 'primeng/tabs';
import {isUriValid} from '../../app.service';

@Component({
  selector: 'object-inspector',
  imports: [
    FormsModule,
    CodeEditor,
    Tabs,
    TabList,
    Tab
  ],
  template: `
    <div class="object-inspector-container">
      @if (entry()) {
        <h2>{{ entry()!.uri }}</h2>
      }
      @if (loadResult().type === EntryLoadResultType.FAILED) {
        <i>Entry is currently not available...</i>
      } @else {

        @if ((loadResult().type === EntryLoadResultType.SINGLE)) {
          <h3>Single Version</h3>
        } @else if (loadResult().type === EntryLoadResultType.MULTI) {
          <p-tabs [(value)]="v" scrollable class="version-tabs">
            <p-tablist>
              @for (version of loadResult().versions; let idx = $index; track idx) {
                <p-tab [value]="idx">{{ idx }}</p-tab>
              }
            </p-tablist>
          </p-tabs>
        }

        <code-editor [ngModel]="oamStr()"
                     [languages]="_languages"
                     [language]="'JSON'"
                     [theme]="service.isDark() ? 'dark' : 'light'"
                     [disabled]="true">
        </code-editor>
      }
    </div>`,
  styles: `
    .version-tabs {
      width: 80vw;
    }`
})
export class ObjectInspector extends AbstractInspector {

  _languages: Array<LanguageDescription> = [LanguageDescription.of({
    name: "JSON",
    alias: ["json5"],
    extensions: ["json", "map"],
    load() {
      return import("@codemirror/lang-json").then(m => m.json())
    }
  })];

  oamStr = computed(() => {
    const _loadResult = this.loadResult();
    if (!_loadResult.type) {
      return '';
    }

    switch (_loadResult.type) {
      case EntryLoadResultType.FAILED:
        return '';
      case EntryLoadResultType.SINGLE:
        return JSON.stringify(_loadResult.versions[0].objectAsMap, null, 2);
      case EntryLoadResultType.MULTI: {
        const versions = _loadResult.versions;
        const version = this.v();
        if (versions && version < versions.length) {
          return JSON.stringify(versions[version].objectAsMap, null, 2);
        } else {
          return '';
        }
      }
    }
  });

  protected readonly EntryLoadResultType = EntryLoadResultType;

  editor = viewChild(CodeEditor);

  @HostListener('window:keydown.alt.i', ['$event'])
  onControlShiftI(event: Event) {
    event.stopPropagation();
    event.stopImmediatePropagation();

    const selection = this.editor()?.view?.state.selection.main;
    if (!selection) {
      return;
    }

    const selectionText = this.oamStr().substring(selection.from, selection.to);
    if (isUriValid(selectionText)) {
      this.service.performAcquire(selectionText);
    } else {
      this.service.msgWarn({
        summary: 'Selection is not a valid URI',
        detail: 'Please select a valid URI to load it.'
      })
    }
  }
}
