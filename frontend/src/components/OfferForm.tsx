import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { offersApi } from '../api/endpoints';
import type { Offer, Posting } from '../api/types';
import { fieldErrors, formError } from '../lib/errors';
import { useCurrencies } from '../lib/queries';
import { Button } from './Button';
import { Field, FieldRow, Input, Select, Textarea } from './Field';
import { Notice } from './Notice';
import styles from './OfferForm.module.css';

export function OfferForm({
  posting,
  offer,
  onDone,
  onCancel,
}: {
  posting: Posting;
  offer?: Offer;
  onDone?: (offer: Offer) => void;
  onCancel?: () => void;
}) {
  const queryClient = useQueryClient();
  const currencies = useCurrencies();
  const fixedCurrency = posting.payCurrency;
  const [text, setText] = useState(offer?.text ?? '');
  const [priceFrom, setPriceFrom] = useState(
    offer ? String(offer.priceFrom) : posting.payFrom !== null ? String(posting.payFrom) : '',
  );
  const [priceTo, setPriceTo] = useState(
    offer ? String(offer.priceTo) : posting.payTo !== null ? String(posting.payTo) : '',
  );
  const [currency, setCurrency] = useState(offer?.currency ?? fixedCurrency ?? 'EUR');

  const mutation = useMutation({
    mutationFn: () => {
      const input = {
        text,
        priceFrom: Number(priceFrom),
        priceTo: Number(priceTo || priceFrom),
        currency: fixedCurrency ? null : currency,
      };
      return offer ? offersApi.update(offer.id, input) : offersApi.create(posting.id, input);
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['offers'] });
      queryClient.invalidateQueries({ queryKey: ['conversations'] });
      onDone?.(saved);
    },
  });
  const errors = fieldErrors(mutation.error);
  const message = formError(mutation.error);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate();
  };

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {message && <Notice tone="error">{message}</Notice>}
      <Field
        label="Your proposal"
        error={errors.text}
        hint="What you’ll deliver, when, and anything the other side should know."
      >
        {(props) => <Textarea {...props} rows={5} value={text} onChange={(e) => setText(e.target.value)} />}
      </Field>
      <FieldRow>
        <Field label="Price from" error={errors.priceFrom ?? errors.priceRangeOrdered}>
          {(props) => (
            <Input
              {...props}
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={priceFrom}
              onChange={(e) => setPriceFrom(e.target.value)}
            />
          )}
        </Field>
        <Field label="Price to" error={errors.priceTo} hint="Same as “from” for a fixed price.">
          {(props) => (
            <Input
              {...props}
              type="number"
              min="0"
              step="0.01"
              inputMode="decimal"
              value={priceTo}
              placeholder={priceFrom}
              onChange={(e) => setPriceTo(e.target.value)}
            />
          )}
        </Field>
      </FieldRow>
      <Field
        label="Currency"
        error={errors.currency}
        hint={fixedCurrency ? 'Offers use the posting’s currency.' : undefined}
      >
        {(props) =>
          fixedCurrency ? (
            <Input {...props} value={fixedCurrency} disabled />
          ) : (
            <Select {...props} value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {(currencies.data ?? []).map((c) => (
                <option key={c.code} value={c.code}>
                  {c.code} · {c.name}
                </option>
              ))}
            </Select>
          )
        }
      </Field>
      <div className={styles.actions}>
        {onCancel && (
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" variant="primary" loading={mutation.isPending}>
          {offer ? 'Update offer' : 'Send offer'}
        </Button>
      </div>
    </form>
  );
}
