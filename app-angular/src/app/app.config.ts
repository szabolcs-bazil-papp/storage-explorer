import {ApplicationConfig, provideZoneChangeDetection} from '@angular/core';
import {provideRouter} from '@angular/router';

import {routes} from './app.routes';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {providePrimeNG} from 'primeng/config';
import Aura from '@primeng/themes/aura';
import Lara from '@primeng/themes/lara';
import {provideHttpClient} from '@angular/common/http';
import {BASE_PATH, ExplorerService} from '../api/se';
import {StorageIndexService} from './services/storage-index.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({eventCoalescing: true}),
    provideRouter(routes),
    provideAnimationsAsync(),
    providePrimeNG({
      inputStyle: 'outlined',
      ripple: true,
      theme: {
        preset: Aura,
      },
    }),
    { provide: BASE_PATH, useValue: 'http://localhost:8080/storageexplorer' },
    provideHttpClient(),
    ExplorerService,
  ],
};
