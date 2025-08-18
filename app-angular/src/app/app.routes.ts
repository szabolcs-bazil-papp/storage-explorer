import {Routes} from '@angular/router';
import {AppLayout} from './layout/app.layout';
import {ArcScript} from './pages/arcscript/arc.script';
import {InspectorContainer} from './pages/inspector/inspector.container';
import {Dashboard} from './pages/dashboard/dashboard';
import {ListInspector} from './pages/inspector/list.inspector';
import {MapInspector} from './pages/inspector/map.inspector';
import {SequenceInspector} from './pages/inspector/sequence.inspector';
import {ObjectInspector} from './pages/inspector/object.inspector';
import {AUTH_GUARD, AUTH_REDIRECT, Login} from './auth/auth';

export const routes: Routes = [
  {path: '', redirectTo: AUTH_REDIRECT, pathMatch: 'full'},
  {path: 'login', component: Login},
  {
    path: 'app',
    component: AppLayout,
    canActivate: [AUTH_GUARD],

    children: [

      {path: 'dashboard', component: Dashboard},

      {path: 'arc-script', component: ArcScript},

      {
        path: 'inspect',
        component: InspectorContainer,
        children: [

          {path: 'l/:id', component: ListInspector},
          {path: 'm/:id', component: MapInspector},
          {path: 's/:id', component: SequenceInspector},
          {path: 'o/:id', component: ObjectInspector},

        ]
      }

    ]
  }
];
