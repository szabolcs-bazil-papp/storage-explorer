import {Component, inject} from '@angular/core';
import {AppService, validatorUri} from '../app.service';
import {Button} from 'primeng/button';
import {FloatLabel} from 'primeng/floatlabel';
import {InputText} from 'primeng/inputtext';
import {FormBuilder, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Message} from 'primeng/message';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

@Component({
  selector: 'load-entry',
  imports: [
    Button,
    FloatLabel,
    InputText,
    FormsModule,
    ReactiveFormsModule,
    Message
  ],
  template: `
    <div class="load-entry-wrapper">
      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="load-entry-form">
        <p-floatlabel variant="on">
          <input pInputText class="w10"
                 id="load_entry_uri"
                 formControlName="uri"
                 [invalid]="isInvalid('uri')"
                 autocomplete="off"/>
          <label for="load_entry_uri">URI</label>
        </p-floatlabel>
        @if (isInvalid('uri')) {
          <p-message class="w10"
                     severity="error"
                     variant="simple"
                     icon="pi pi-times-circle">
            Invalid URI!
          </p-message>
        }
      </form>
      <div class="load-entry-footer">
        <p-button label="Cancel" severity="secondary" (onClick)="onCancel()"></p-button>
        <p-button label="Load"
                  [disabled]="isInvalid('uri')"
                  severity="primary"
                  (onClick)="onSubmit()"></p-button>
      </div>
    </div>`,
  styles: `
  .load-entry-wrapper {
    display: flex;
    flex-direction: column;
  }
  .load-entry-form {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    padding: 2rem;
  }

  .w10 {
    min-width: 40em;
  }
  .load-entry-footer {
    display: flex;
    flex-direction: row;
    justify-content: flex-end;
    gap: 1rem;
  }`
})
export class LoadEntry {

  service = inject(AppService);
  formBuilder = inject(FormBuilder);
  formSubmitted = false;
  form = this.formBuilder.group({
    uri: ['', [validatorUri]],
  });

  constructor(private readonly ref: DynamicDialogRef) {
  }

  onSubmit() {
    this.formSubmitted = true;
    if (this.form.valid) {
      this.ref.close(this.form.value.uri);
      this.form.reset({uri: ''})
      this.formSubmitted = false;
    }
  }

  onCancel() {
    this.ref.close(null);
  }

  isInvalid(controlName: string) {
    const control = this.form.get(controlName);
    return control?.invalid && (control.touched || this.formSubmitted);
  }

}
