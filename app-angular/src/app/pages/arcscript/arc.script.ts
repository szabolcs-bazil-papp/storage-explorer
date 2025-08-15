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

import {Component, HostListener, inject, signal, viewChild} from '@angular/core';
import {ScrollPanel} from 'primeng/scrollpanel';
import {Fieldset} from 'primeng/fieldset';
import {FormsModule} from '@angular/forms';
import 'prismjs/components/index';
import {CodeEditor} from '@acrodata/code-editor';
import {
  LanguageDescription,
  LanguageSupport,
  StreamLanguage,
  StreamParser
} from "@codemirror/language";
import {Button} from 'primeng/button';
import {AppService} from '../../app.service';

function legacy(parser: StreamParser<unknown>): LanguageSupport {
  return new LanguageSupport(StreamLanguage.define(parser))
}

@Component({
  selector: 'arc-script',
  imports: [
    ScrollPanel,
    Fieldset,
    FormsModule,
    CodeEditor,
    Button
  ],
  template: `
    <p-scroll-panel
      [style]="{ height: '100%', width: '100%', 'padding-left': '1rem', 'padding-right': '1rem'}">
      <p-fieldset>
        <ng-template #header>
          <div class="arc-script-header p-fieldset-legend">
            <span class="p-fieldset-legend-label">ArcScript</span>
            <p-button severity="primary"
                      icon="pi pi-play"
                      label="Execute"
                      (onClick)="onPlayClicked()"
                      [disabled]="inProgress()">
            </p-button>
          </div>
        </ng-template>
        <code-editor [(ngModel)]="service.scriptText"
                     [languages]="_languages"
                     [theme]="service.isDark() ? 'dark' : 'light'"
                     [language]="'Groovy'"
                     [disabled]="inProgress()"></code-editor>
      </p-fieldset>
    </p-scroll-panel>
  `,
  styles: `
    .arc-script-header {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: unset
    }
  `
})
export class ArcScript {

  _languages: Array<LanguageDescription> = [LanguageDescription.of({
    name: "Groovy",
    extensions: ["groovy", "gradle"],
    filename: /^Jenkinsfile$/,
    load() {
      return import("@codemirror/legacy-modes/mode/groovy").then(m => legacy(m.groovy))
    }
  })];

  editor = viewChild.required(CodeEditor);
  inProgress = signal(false);

  service = inject(AppService);


  onPlayClicked() {
    this.inProgress.set(true)
    this.service.performExecuteScript().then(ok => {
      this.inProgress.set(false);
    });
  }

  @HostListener('window:keydown.control.enter', ['$event'])
  onControlEnter(event: Event) {
    event.stopPropagation();
    const e = event as KeyboardEvent;
    if (e.key === 'Enter' && e.ctrlKey) {
      this.onPlayClicked();
    }
  }

}
