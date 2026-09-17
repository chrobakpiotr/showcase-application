import { bootstrapApplication } from '@angular/platform-browser';
import { enableProdMode, provideZoneChangeDetection } from '@angular/core';
import { mergeApplicationConfig, ApplicationConfig } from '@angular/core';

import { environment } from '@environments/environment';
import { AppComponent } from '@app/app.component';
import { appConfig } from '@app/app.config';

if (environment.production) {
  enableProdMode();
}

const zoneConfig: ApplicationConfig = {
  providers: [provideZoneChangeDetection()],
};

bootstrapApplication(
  AppComponent,
  mergeApplicationConfig(appConfig, zoneConfig)
).catch((err) => console.error(err));
