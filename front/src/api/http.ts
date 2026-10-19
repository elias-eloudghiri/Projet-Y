export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    message: string,
  ) {
    super(message)
  }
}

const NO_REFRESH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/logout',
  '/api/auth/guest',
]

let refreshing: Promise<boolean> | null = null

/** One refresh at a time: concurrent 401s wait for the same rotation. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST', credentials: 'same-origin' })
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function send(method: string, path: string, body?: unknown): Promise<Response> {
  // FormData (file upload): the browser sets the multipart Content-Type with its boundary.
  if (body instanceof FormData) {
    return fetch(path, { method, credentials: 'same-origin', body })
  }
  return fetch(path, {
    method,
    credentials: 'same-origin',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

async function toError(response: Response): Promise<ApiError> {
  const data = await response.json().catch(() => null)
  return new ApiError(response.status, data?.code, data?.message ?? 'Une erreur est survenue.')
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response = await send(method, path, body)

  if (response.status === 401 && !NO_REFRESH_PATHS.includes(path)) {
    // Even if this refresh lost a race against another tab, that tab has updated the shared cookies:
    // retrying once is enough in both cases.
    await refreshSession()
    response = await send(method, path, body)
  }

  if (!response.ok) {
    throw await toError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
