import { HttpInterceptorFn } from '@angular/common/http';

/**
 * El JWT viaja en una cookie HttpOnly gestionada por el navegador; este
 * interceptor solo asegura que withCredentials vaya en todas las peticiones
 * para que esa cookie se envie y se reciba.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req.clone({ withCredentials: true }));
};
