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

import {CanActivateFn, RedirectFunction, Router} from '@angular/router';
import {HttpEvent, HttpEventType, HttpHandlerFn, HttpRequest} from '@angular/common/http';
import {Component, HostListener, inject, signal} from '@angular/core';
import {ExplorerService} from '../../api/se';
import {Fieldset} from 'primeng/fieldset';
import {FormBuilder, ReactiveFormsModule} from '@angular/forms';
import {lastValueFrom, Observable, tap} from 'rxjs';
import {FloatLabel} from 'primeng/floatlabel';
import {InputText} from 'primeng/inputtext';
import {AutoFocus} from 'primeng/autofocus';
import {Message} from 'primeng/message';
import {Button} from 'primeng/button';

const TOKEN_KEY = 'storage-explorer-token';

export const AUTH_REDIRECT: RedirectFunction = activatedRouteSnapshot => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  return (!!token && token.length > 0) ? 'app/dashboard' : 'login'
}

export const AUTH_GUARD: CanActivateFn = (route, state) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  return (!!token && token.length > 0) ? true : inject(Router).parseUrl('login');
}

export function tokenInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (!token || token.length < 1) {
    return next(req);
  }

  req = req.clone({
    setHeaders: {
      Authorization: token,
    }
  });
  return next(req);
}

export function responseInterceptor(req: HttpRequest<unknown>, next: HttpHandlerFn): Observable<HttpEvent<unknown>> {
  return next(req).pipe(tap(event => {
    if (event.type === HttpEventType.Response && (event.status === 401 || event.status === 403)) {
      inject(Router).navigateByUrl('login');
    }
  }))
}

export function onLogOut(router: Router) {
  sessionStorage.removeItem(TOKEN_KEY);
  router.navigateByUrl('login');
}

const taglines: Array<string> = [
  'Answering ObjectNodeReference.isPresent()? since 2025',
  'Silently judges the size of your temp schema',
  'Navigating graph arcs in a flash',
  'To see the aftermath of your ObjectModificationExceptions',
  'Query. Verify. Fix. Repeat.',
  'By the people, for the people',
  'Blazingly fast - unless querying AsyncInvocationRequests',
  '"Much wow" - Anonymous',

]

@Component({
  selector: 'login',
  template: `
    <div class="login-wrapper">
      <div class="hero-wrapper">
        <h1 class="hero-title">Storage Explorer</h1>
        <h2 class="hero-description">{{ tagline() }}</h2>
      </div>
      <div class="form-wrapper">
        <p-fieldset class="form-fieldset">
          <ng-template #header>
            <h2>Login</h2>
          </ng-template>
          <form [formGroup]="form" (ngSubmit)="onSubmit()" class="login-form">
            <p-floatlabel variant="on">
              <input pInputText
                     id="login_form_username"
                     formControlName="username"
                     autocomplete="off"
                     [pAutoFocus]="true"/>
              <label for="login_form_username">Username</label>
            </p-floatlabel>
            <p-floatlabel variant="on">
              <input pInputText
                     id="login_form_username"
                     formControlName="password"
                     type="password"
                     autocomplete="off"/>
              <label for="login_form_username">Password</label>
            </p-floatlabel>
            @if (errMsg()) {
              <p-message severity="error"
                         variant="simple"
                         icon="pi pi-times-circle">
                {{ errMsg() }}
              </p-message>
            }
          </form>
          <div class="login-form-controls">
            <p-button label="Login"
                      severity="primary"
                      (onClick)="onSubmit()"
                      [disabled]="formSubmitted">
            </p-button>
          </div>
        </p-fieldset>
      </div>
    </div>`,
  imports: [
    Fieldset,
    ReactiveFormsModule,
    FloatLabel,
    InputText,
    AutoFocus,
    Message,
    Button
  ],
  styles: `
    .login-wrapper {
      display: flex;
      height: 100vh;
    }

    .hero-wrapper {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      flex: 3;
      background: linear-gradient(135deg, var(--p-primary-color) 0%, var(--p-primary-200) 100%);
      animation: gradientShift 12s ease infinite;
    }

    .hero-title {
      font-size: 7em;
      font-weight: 900;
      margin-bottom: 1rem;
    }

    .hero-description {
      font-size: 2em;
      font-style: italic;
      margin-top: 1rem;
    }

    .form-wrapper {
      display: flex;
      flex: 2;
      justify-content: center;
      align-items: center;
      background: var(--p-fieldset-background);
    }

    .form-fieldset {
    }

    .login-form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 0 1rem 1rem;
    }

    .login-form-controls {
      display: flex;
      justify-content: flex-end;
      padding: 0 1rem;
    }`
})
export class Login {

  private readonly api = inject(ExplorerService);
  private readonly router = inject(Router);
  formBuilder = inject(FormBuilder);

  formSubmitted = false;
  form = this.formBuilder.group({
    username: [''],
    password: ['']
  });
  errMsg = signal<string | null>(null);
  tagline = signal(taglines[Math.floor(Math.random() * taglines.length)]);

  onSubmit() {
    this.formSubmitted = true;
    if (this.form.valid) {
      const username = this.form.value.username ?? '';
      const password = this.form.value.password ?? '';
      lastValueFrom(this.api.verify({username, password}))
        .then(ok => {
          const token = ok.token;
          if (!token) {
            this.errMsg.set('Invalid username or password');
          } else {
            sessionStorage.setItem(TOKEN_KEY, token);
          }
        })
        .then(() => {
          this.form.reset({
            username: '',
            password: ''
          });
          this.formSubmitted = false;
          return this.router.navigateByUrl('app/dashboard');
        })
        .catch(err => {
          this.errMsg.set('Invalid username or password');
          console.error(err);
          this.formSubmitted = false;
        });
    }
  }

  @HostListener('window:keydown.enter', ['$event'])
  onEnter(event: Event) {
    this.onSubmit();
  }


}
