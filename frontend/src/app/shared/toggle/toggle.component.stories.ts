import { storiesOf, moduleMetadata } from '@storybook/angular';
import { withKnobs } from '@storybook/addon-knobs';
import { withA11y } from '@storybook/addon-a11y';

import { ToggleComponent } from './toggle.component';
import { IconComponent } from '../icon/icon.component';

const stories = storiesOf('Base | Toggle', module);

stories.addDecorator(withKnobs);
stories.addDecorator(withA11y);
stories.addDecorator(
  moduleMetadata({
    declarations: [ToggleComponent, IconComponent],
    imports: []
  })
);

stories.add('default', () => ({
  component: ToggleComponent,
  props: {

  }
}));
