import { CircleAlert } from 'lucide-react';

import { Alert } from '@/components/ui/alert';

export function ErrorAlert({ message }: { message: string }) {
  return (
    <Alert variant="destructive">
      <CircleAlert aria-hidden />
      <span>{message}</span>
    </Alert>
  );
}
