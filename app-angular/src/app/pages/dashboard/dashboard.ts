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

import {Component} from '@angular/core';
import {Fieldset} from 'primeng/fieldset';
import {ScrollPanel} from 'primeng/scrollpanel';
import {Avatar} from 'primeng/avatar';

@Component({
  selector: 'dashboard',
  imports: [
    Fieldset,
    ScrollPanel,
    Avatar
  ],
  template: `
    <p-scroll-panel
      [style]="{ height: '100%', width: '100%', 'padding-left': '1rem', 'padding-right': '1rem'}">
      <p-fieldset legend="Dashboard">
        <h3>Query the Storage</h3>
        <p>Using the
          <p-avatar icon="pi pi-code" shape="circle"></p-avatar>
          <b>ArcScript</b> page, issue queries to the storage. You can find documentation on how to
          use the query language <a
            href="https://github.com/szabolcs-bazil-papp/storage-explorer/wiki/ArcScript">on the
            wiki.</a>
        </p>
        <p>Queries may be executed using the <code>Ctrl+Enter</code> hotkey.</p>
        <h3>Inspect entries</h3>
        <p>Double clicking any row in your query results will load the data associated with the
          entry in an
          <p-avatar icon="pi pi-compass" shape="circle"></p-avatar>
          <b>Inspector</b>.
        </p>
        <p>You may directly load an exact, versionless <code>URI</code> by clicking the
          <p-avatar icon="pi pi-spinner-dotted" shape="circle"></p-avatar>
          button in the tree, or using the <code>Control+Shift+L</code> hotkey.
        </p>
        <p>If you have many
          <p-avatar icon="pi pi-compass" shape="circle"></p-avatar>
          <b>Inspector</b>s open, you can search and filter them by clicking the
          <p-avatar icon="pi pi-search" shape="circle"></p-avatar>
          button in the nav bar.
        </p>
        <i>
          <h3>Known quirks</h3>
          <ul>
            <li>
              <p>If you specify <code>show</code> commands in a query, you must</p>
              <ol>
                <li>Specify the <code>URI</code> property explicitly, such as <code
                  class="code-block">show 'uri' as
                  'uri'</code></li>
                <li>Always specify an alias ( <code class="code-block">as</code> ) for your
                  properties - these aliases
                  must not contain dots.
                </li>
              </ol>
            </li>
            <li>Sometimes the scrollbars on
              <p-avatar icon="pi pi-compass" shape="circle"></p-avatar>
              <b>Inspector</b> tabs glitch out and will not appear. This is a side effect of how
              PrimeNG detects if any tabs are not in the viewport. This will eventually solved when
              I move away from the PrimeNG component, until then, I'm very sorry!
            </li>
          </ul>
        </i>
        <h3>What's next?</h3>
        <p>As you can see, this page is currently <strong>anything but a dashboard</strong>. I
          plan to prefetch schema and type information and display it here.</p>
      </p-fieldset>
    </p-scroll-panel>`
})
export class Dashboard {

}
