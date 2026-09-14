import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const url = new URL(request.url, window.location.origin);
  const isApi = url.origin === window.location.origin && url.pathname.startsWith('/api/');
  const isLogin = url.pathname === '/api/auth/login';
  const token = isApi && !isLogin ? auth.accessToken() : null;
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(authenticatedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isApi && !isLogin) {
        if (error.status === 401 && token && auth.accessToken() === token) {
          auth.logout('Your session expired. Sign in again to continue.');
        } else if (error.status === 403) {
          auth.message.set('Your account does not have access to this operation.');
        }
      }
      return throwError(() => error);
    }),
  );
};
