import 'package:react_web/model/owner_info.dart';
import 'package:react_web/model/sampler_sample.dart';
import 'package:test/test.dart';

SamplerSample _sample(String id, double value, {bool available = true}) =>
    SamplerSample(
      id: id,
      name: id,
      suffix: 'ms/s',
      value: value,
      display: '$value',
      min: value,
      max: value,
      history: const <double>[],
      available: available,
    );

OwnerInfo _owner(String id, {String? includedIn}) => OwnerInfo(
  id: id,
  name: id,
  version: '1',
  enabled: true,
  samplerId: 'mod-${includedIn ?? id}',
  includedIn: includedIn,
);

void main() {
  test('fromJson falls back to the id for a missing name', () {
    final OwnerInfo owner = OwnerInfo.fromJson(<String, dynamic>{
      'id': 'create',
      'version': '6.0',
      'enabled': false,
      'samplerId': 'mod-create',
    });
    expect(owner.name, 'create');
    expect(owner.enabled, isFalse);
    expect(owner.includedIn, isNull);
  });

  test('ownerCosts shares the measured cost, costliest first', () {
    final List<OwnerCost> costs = ownerCosts(
      <OwnerInfo>[
        _owner('alpha'),
        _owner('beta'),
        _owner('beta-part', includedIn: 'beta'),
        _owner('react'),
      ],
      <String, SamplerSample>{
        'mod-alpha': _sample('mod-alpha', 1),
        'mod-beta': _sample('mod-beta', 3),
      },
    );
    expect(costs.map((OwnerCost cost) => cost.owner.id).toList(), <String>[
      'beta',
      'alpha',
      'beta-part',
      'react',
    ]);
    expect(costs[0].share, closeTo(0.75, 1e-9));
    expect(costs[1].share, closeTo(0.25, 1e-9));
    expect(costs[2].msPerSecond, isNull);
    expect(costs[3].msPerSecond, isNull);
    expect(costs[3].share, 0);
  });

  test('ownerCosts gives no share while nothing is measured', () {
    final List<OwnerCost> costs = ownerCosts(
      <OwnerInfo>[_owner('alpha'), _owner('beta')],
      <String, SamplerSample>{
        'mod-alpha': _sample('mod-alpha', 0),
        'mod-beta': _sample('mod-beta', 2, available: false),
      },
    );
    expect(costs.first.owner.id, 'alpha');
    expect(costs.first.msPerSecond, 0);
    expect(costs.first.share, 0);
    expect(costs.last.msPerSecond, isNull);
  });
}
