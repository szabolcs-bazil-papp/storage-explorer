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


import {Component, inject} from '@angular/core';
import {Splitter} from 'primeng/splitter';
import {RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {ScriptResult} from './script.result';
import {Button} from 'primeng/button';
import {Tooltip} from 'primeng/tooltip';
import {AppTree} from './app.tree';
import {NgOptimizedImage} from '@angular/common';
import {AppService, KEY_DARK_MODE} from '../app.service';

@Component({
  selector: 'app-layout',
  imports: [
    Splitter,
    RouterOutlet,
    ScriptResult,
    RouterLink,
    RouterLinkActive,
    Button,
    Tooltip,
    AppTree,
    NgOptimizedImage
  ],
  template: `
    <div [class]="{ 'layout-wrapper': true, 'my-app-dark': service.isDark() }">

      <header class="layout-header">
        <a routerLink="/" class="logo-link">
          <img class="logo-icon" ngSrc="favicon.png" [width]="32" [height]="32"/>
          <span class="logo-text">Storage Explorer</span>
        </a>
        <span class="spacer"></span>
        <nav class="main-nav">
          <a routerLink="/" class="nav-link" routerLinkActive="active">
            <i class="pi pi-home"></i>
            <span>Dashboard</span>
          </a>
        </nav>
        <div class="header-actions">
          <p-button [icon]="service.isDark() ? 'pi pi-sun' : 'pi pi-moon'"
                    class="dark-mode-toggle"
                    severity="secondary"
                    (onClick)="toggleDarkMode()"
                    pTooltip="asd">

          </p-button>
        </div>
      </header>

      <main class="app-main">

        <app-tree></app-tree>

        <p-splitter layout="vertical" class="layout-splitter">
          <ng-template #panel>
            <router-outlet></router-outlet>
          </ng-template>
          <ng-template #panel>
            <script-result></script-result>
          </ng-template>
        </p-splitter>

      </main>
      <footer class="app-footer">

        <div class="footer-content">
          <span>Storage Explorer v0.5.0</span>
          <a href="https://github.com/szabolcs-bazil-papp/storage-explorer"
             target="_blank"
             rel="noopener noreferrer"
             class="footer-link github-link">
            <i class="pi pi-github"></i>
            <span>GitHub</span>
          </a>
        </div>

      </footer>
    </div>`,
  styles: `
    .layout-wrapper {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
      background-color: var(--surface-ground);
    }

    .layout-header {
      display: flex;
      align-items: center;
      background-color: rgba(from var(--primary-color) r g b / 0.6);
      backdrop-filter: blur(10px);
      border-bottom: 1px solid var(--primary-color);
      color: white;
      padding: 0 2rem;
      box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      height: 60px;
    }

    .logo-link {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      text-decoration: none;
      color: white;
      font-weight: 600;
      font-size: 1.25rem;
    }

    .logo-icon {
      display: flex;
      align-items: center;
      font-size: 1.5rem;
    }

    .main-nav {
      display: flex;
      gap: 1.5rem;
      margin-left: 2rem;
    }

    .nav-link {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      /*color: rgba(255, 255, 255, 0.8);*/
      text-decoration: none;
      padding: 0.5rem 0;
      border-bottom: 2px solid transparent;
      transition: all 0.2s ease;
    }

    .nav-link:hover {
      color: white;
    }

    .nav-link.active {
      color: white;
      border-bottom-color: white;
    }

    .nav-link i {
      font-size: 1.1rem;
    }

    .header-actions {
      display: flex;
      gap: 1rem;
      align-items: center;
    }

    .dark-mode-toggle {
      background-color: rgba(255, 255, 255, 0.1) !important;
      border: none !important;
      color: white !important;
      margin-left: 2rem;
    }

    .dark-mode-toggle {
      background-color: rgba(255, 255, 255, 0.1) !important;
      border: none !important;
      color: white !important;
      margin-left: 2rem;
    }

    .dark-mode-toggle:hover {
      background-color: rgba(255, 255, 255, 0.2) !important;
    }

    .app-main {
      display: flex;
      flex: 1;
      margin: 0 auto;
      width: 100%;
      height: 100%;
      max-height: calc(100vh - 100px);
    }

    .layout-splitter {
      flex: 1;
      height: calc(100vh - 100px);
    }

    .app-footer {
      background-color: var(--p-surface-card);
      padding: 1rem 2rem;
      border-top: 1px solid var(--p-surface-border);
      height: 40px;
    }

    .footer-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin: 0 auto;
      color: var(--p-text-color-secondary);
      font-size: 0.9rem;
    }

    .footer-links {
      display: flex;
      gap: 1.5rem;
    }

    .footer-link {
      color: var(--p-text-color-secondary);
      text-decoration: none;
      transition: color 0.2s ease;
    }

    .footer-link:hover {
      color: var(--p-primary-color);
    }

    /* Dark Mode Styles */
    .my-app-dark .app-header {
      background-color: rgba(from var(--p-primary-800) r g b / 0.6);
    }

    .my-app-dark .nav-link {
      color: rgba(255, 255, 255, 0.7);
    }

    .my-app-dark .nav-link:hover,
    .my-app-dark .nav-link.active {
      color: white;
    }

    .my-app-dark .dark-mode-toggle {
      background-color: rgba(255, 255, 255, 0.05) !important;
    }

    .my-app-dark .app-footer {
      background-color: var(--p-surface-800);
      border-top-color: var(--p-surface-700);
    }

    .my-app-dark .github-link {
      color: var(--p-text-color-secondary);
    }

    .my-app-dark .github-link:hover {
      color: var(--p-primary-color);
    }
  `
})
export class AppLayout {

  readonly service = inject(AppService);

  toggleDarkMode() {
    this.service.isDark.update(it => {
      const newVal = !it;
      document.documentElement.classList.toggle('my-app-dark');
      localStorage.setItem(KEY_DARK_MODE, newVal.toString());
      return newVal;
    })
  }

}
