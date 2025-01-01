import {
  AfterViewInit, Component, ElementRef, input, OnChanges, SimpleChanges,
  ViewChild,
} from '@angular/core';
import * as Prism from 'prismjs';
import 'prismjs/components/index'
import 'prismjs/components/prism-json';


@Component({
  selector: 'app-prism',
  imports: [],
  standalone: true,
  template: `
    <pre class="language-json">
    <code #codeElement class="language-json">{{ code() }}</code>
  </pre>`,
  styles: ``,
})
export class PrismComponent implements AfterViewInit, OnChanges {

  code = input.required<string>();
  @ViewChild('codeElement') codeElement!: ElementRef;

  constructor() {
  }

  ngAfterViewInit(): void {
    Prism.highlightElement(this.codeElement.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes && changes.hasOwnProperty('code')) {
      if (this.codeElement?.nativeElement) {
        this.codeElement.nativeElement.textContent = this.code();
        Prism.highlightElement(this.codeElement.nativeElement);
      }
    }
  }

}

