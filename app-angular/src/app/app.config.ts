import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection
} from '@angular/core';
import {
  provideRouter,
  withEnabledBlockingInitialNavigation,
  withInMemoryScrolling
} from '@angular/router';
import {routes} from './app.routes';
import Aura from '@primeuix/themes/aura';
import {
  HttpHandlerFn,
  HttpRequest,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import {providePrimeNG} from 'primeng/config';
import {definePreset} from '@primeuix/themes';
import {provideAnimationsAsync} from '@angular/platform-browser/animations/async';
import {MessageService} from 'primeng/api';

declare const STORAGE_EXPLORER_API_PATH: string;

const Theme = definePreset(Aura, {
  primitive: {
    cyan: {
      50: '#f4ffff',
      100: '#d7f6f6',
      200: '#bdeeee',
      300: '#a3e5e5',
      400: '#88dddd',
      500: '#6ed6d6',
      600: '#54cccc',
      700: '#3ac4c4',
      800: '#1fbbbb',
      900: '#05b3b3',
      950: '#059999',
    },
    slate: {
      50: '#f4ffff',
      100: '#dce3e3',
      200: '#c4d2d2',
      300: '#afc3c3',
      400: '#99b3b3',
      500: '#84a4a4',
      600: '#6e9595',
      700: '#598585',
      800: '#437676',
      900: '#2e6666',
      950: '#185757'
    }
  },
  semantic: {
    primary: {
      50: '{cyan.50}',
      100: '{cyan.100}',
      200: '{cyan.200}',
      300: '{cyan.300}',
      400: '{cyan.400}',
      500: '{cyan.500}',
      600: '{cyan.600}',
      700: '{cyan.700}',
      800: '{cyan.800}',
      900: '{cyan.900}',
      950: '{cyan.950}'
    }
  }
})

export function apiRequestInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn) {
  req = req.clone({
    url: req.url.replace('http://localhost', STORAGE_EXPLORER_API_PATH)
  });
  return next(req);
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideAnimationsAsync(),
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({eventCoalescing: true}),
    provideRouter(routes, withInMemoryScrolling({
      anchorScrolling: 'enabled',
      scrollPositionRestoration: 'enabled'
    }), withEnabledBlockingInitialNavigation()),
    provideHttpClient(withInterceptors([apiRequestInterceptor])),
    providePrimeNG({theme: {preset: Theme, options: {darkModeSelector: '.my-app-dark'}}}),
    MessageService
  ]
};
